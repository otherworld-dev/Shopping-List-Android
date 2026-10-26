package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.guest.GuestRepository
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.ItemEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.MoveItemRequest
import dev.otherworld.shoppinglist.data.sync.CheckPayload
import dev.otherworld.shoppinglist.data.sync.ItemCreatePayload
import dev.otherworld.shoppinglist.data.sync.ItemUpdatePayload
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.ReorderPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
import dev.otherworld.shoppinglist.data.sync.areaToSend
import dev.otherworld.shoppinglist.domain.guest.GuestIds
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists whose items on the phone are out of date: the server counts a different number of unticked
 * items than the phone has (a list never opened here has none). Lists with changes still queued
 * are left alone, as their count is meant to differ until the queue reaches the server.
 */
internal fun listsWithStaleItems(server: Map<Long, Int>, local: Map<Long, Int>, pending: Set<Long>): List<Long> =
    server.filter { (id, count) -> id !in pending && count != (local[id] ?: 0) }.keys.toList()

@Singleton
class ItemRepository @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val sync: SyncEngine,
    private val tempIds: TempIds,
    private val json: Json,
    private val guests: GuestRepository,
) {
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()

    fun observeItems(listId: Long): Flow<List<ItemModel>> =
        itemDao.observeByList(listId).map { rows -> rows.map { it.toModel() } }

    fun observeAreas(listId: Long): Flow<List<ShopAreaModel>> =
        areaDao.observeByList(listId).map { rows -> rows.map { it.toModel() } }

    /**
     * Fetches items + areas for a synced list and reconciles them into Room. Areas and items are
     * fetched concurrently and written in separate transactions so the (usually fast) areas land
     * immediately even when the items request is slow. Runs un-cancellable so navigating away
     * mid-fetch still populates the cache for next time.
     */
    /**
     * Fetches the items of every list whose count from the server ([serverCounts], list id to
     * unticked items) doesn't match the phone's, so the lists screen shows the right numbers.
     */
    suspend fun refreshStale(serverCounts: Map<Long, Int>) {
        if (serverCounts.isEmpty()) return
        val local = itemDao.uncheckedCounts().associate { it.listId to it.count }
        val pending = mutationDao.listsWithPendingItems().toSet()
        listsWithStaleItems(serverCounts, local, pending).forEach { refresh(it) }
    }

    suspend fun refresh(listId: Long) {
        if (GuestIds.isGuest(listId)) {
            guests.refreshItems(listId)
            return
        }
        if (listId <= 0) return // temp list never reached the server
        withContext(NonCancellable) {
            coroutineScope {
                launch {
                    // A queued area reorder owns the local order until it syncs; don't let the
                    // server's (stale) order clobber it.
                    if (mutationDao.pendingAreaCount(listId) > 0) return@launch
                    val areas = service.getAreas(listId).ocs.data
                    db.withTransaction {
                        if (mutationDao.pendingAreaCount(listId) > 0) return@withTransaction
                        areaDao.deleteByList(listId)
                        areaDao.upsertAll(areas.map { it.toEntity(listId) })
                    }
                }
                launch {
                    val items = service.getItems(listId).ocs.data
                    val pending = mutationDao.pendingItemIds(listId).toSet()
                    db.withTransaction {
                        val serverIds = items.map { it.id }.toSet()
                        val toDelete = itemDao.getByList(listId).map { it.id }
                            .filter { it > 0 && it !in serverIds && it !in pending }
                        itemDao.deleteByIds(toDelete)
                        items.forEach { if (it.id !in pending) itemDao.upsert(it.toEntity()) }
                    }
                }
            }
        }
        sync.requestSync()
    }

    /**
     * Adds an item. A null [quantity] is a name typed on its own: the server fills in the amount
     * it remembers for the name, so the row shows none until then. A list kept on this phone has
     * no server to ask, so it gets the plain 1.
     */
    suspend fun createItem(
        listId: Long,
        name: String,
        quantity: String? = null,
        unit: String? = null,
        shopAreaId: Long? = null,
        areaExplicit: Boolean = false,
        checked: Boolean = false,
        /** The list's areas weren't on the phone yet, so detect the area when the create syncs. */
        detectAreaOnSync: Boolean = false,
        /** [shopAreaId] is the list's default area: shown here, but left for the server to apply. */
        areaIsListDefault: Boolean = false,
    ): ItemModel {
        val id = tempIds.next()
        val entity = ItemEntity(
            id = id,
            listId = listId,
            name = name,
            quantity = quantity ?: "1".takeIf { listDao.isLocal(listId) },
            unit = unit,
            shopAreaId = shopAreaId,
            checked = checked,
            checkedBy = null,
            sortOrder = itemDao.maxSortOrder(listId) + 1,
            updatedAt = null,
        )
        // One transaction, so a refresh never sees the row without its queued create and drops it.
        db.withTransaction {
            itemDao.upsert(entity)
            enqueue(
                MutationTypes.CREATE, id, listId,
                json.encodeToString(
                    ItemCreatePayload.serializer(),
                    ItemCreatePayload(
                        name, quantity, unit, areaToSend(shopAreaId, areaIsListDefault), areaExplicit, checked,
                        detectArea = detectAreaOnSync && shopAreaId == null && !areaExplicit,
                        quantityExplicit = quantity != null,
                    ),
                ),
            )
        }
        sync.requestSync()
        return entity.toModel()
    }

    suspend fun updateItem(
        item: ItemModel,
        name: String? = null,
        quantity: String? = null,
        shopAreaId: Long? = null,
        areaExplicit: Boolean? = null,
        /** The user changed the amount, so the server remembers it. Never for a merged total. */
        quantityExplicit: Boolean? = null,
        /** Takes the item out of its area. A null [shopAreaId] alone leaves the area as it is. */
        clearArea: Boolean = false,
    ) {
        itemDao.getById(item.id)?.let { cur ->
            itemDao.update(
                cur.copy(
                    name = name ?: cur.name,
                    quantity = quantity ?: cur.quantity,
                    shopAreaId = if (clearArea) null else shopAreaId ?: cur.shopAreaId,
                ),
            )
        }
        enqueue(
            MutationTypes.UPDATE, item.id, item.listId,
            json.encodeToString(
                ItemUpdatePayload.serializer(),
                ItemUpdatePayload(
                    name = name, quantity = quantity, shopAreaId = shopAreaId.takeUnless { clearArea },
                    areaExplicit = areaExplicit, quantityExplicit = quantityExplicit, clearArea = clearArea,
                ),
            ),
        )
        sync.requestSync()
    }

    suspend fun check(item: ItemModel, checked: Boolean) {
        itemDao.getById(item.id)?.let { cur ->
            // Who ticked it is the server's to say: drop the old name now, the next refresh brings the new one.
            itemDao.update(
                cur.copy(
                    checked = checked,
                    checkedBy = if (checked) cur.checkedBy else null,
                    checkedByName = null,
                    checkedByGuest = false,
                ),
            )
        }
        enqueue(
            MutationTypes.CHECK, item.id, item.listId,
            json.encodeToString(CheckPayload.serializer(), CheckPayload(checked)),
        )
        sync.requestSync()
    }

    suspend fun deleteItem(item: ItemModel) {
        db.withTransaction {
            itemDao.deleteById(item.id)
            if (item.id < 0) {
                mutationDao.deleteByTarget(MutationEntities.ITEM, item.id)
            } else {
                insertMutation(MutationTypes.DELETE, item.id, item.listId, "{}")
            }
        }
        sync.requestSync()
    }

    /**
     * Moves an item to another list. Online-direct, matching the web app: a cross-list move
     * isn't offline-queueable (the target list's state is unknown offline), so callers check
     * connectivity first. The item leaves the source list at once; the target list picks it up
     * on its next refresh.
     */
    suspend fun moveItem(item: ItemModel, targetListId: Long) {
        service.moveItem(item.listId, item.id, MoveItemRequest(targetListId))
        itemDao.deleteById(item.id)
    }

    suspend fun clearChecked(listId: Long) {
        // The public link API has no bulk endpoints; each item is deleted on its own.
        if (GuestIds.isGuest(listId)) {
            itemDao.getByList(listId).filter { it.checked }.forEach { deleteItem(it.toModel()) }
            return
        }
        itemDao.deleteCheckedByList(listId)
        enqueue(MutationTypes.CLEAR_CHECKED, listId, listId, "{}")
        sync.requestSync()
    }

    suspend fun uncheckAll(listId: Long) {
        if (GuestIds.isGuest(listId)) {
            itemDao.getByList(listId).filter { it.checked }.forEach { check(it.toModel(), false) }
            return
        }
        itemDao.uncheckAllByList(listId)
        enqueue(MutationTypes.UNCHECK_ALL, listId, listId, "{}")
        sync.requestSync()
    }

    /** Persists a new ordering of item ids within a list. */
    suspend fun reorder(listId: Long, orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                itemDao.getById(id)?.let { itemDao.update(it.copy(sortOrder = index)) }
            }
        }
        enqueue(
            MutationTypes.REORDER, listId, listId,
            json.encodeToString(ReorderPayload.serializer(), ReorderPayload(orderedIds)),
        )
        sync.requestSync()
    }

    /** Queues a change for the server, unless its list is kept on this phone only. */
    private suspend fun enqueue(type: String, targetId: Long, listId: Long, payload: String) {
        if (listDao.isLocal(listId)) return
        insertMutation(type, targetId, listId, payload)
    }

    private suspend fun insertMutation(type: String, targetId: Long, listId: Long, payload: String) {
        mutationDao.insert(
            MutationEntity(
                entity = MutationEntities.ITEM,
                type = type,
                targetId = targetId,
                listId = listId,
                payload = payload,
            ),
        )
    }
}
