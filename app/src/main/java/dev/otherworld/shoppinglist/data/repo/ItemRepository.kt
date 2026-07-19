package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.ItemEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.sync.CheckPayload
import dev.otherworld.shoppinglist.data.sync.ItemCreatePayload
import dev.otherworld.shoppinglist.data.sync.ItemUpdatePayload
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.ReorderPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
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

@Singleton
class ItemRepository @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val sync: SyncEngine,
    private val tempIds: TempIds,
    private val json: Json,
) {
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
    suspend fun refresh(listId: Long) {
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

    suspend fun createItem(
        listId: Long,
        name: String,
        quantity: String? = null,
        unit: String? = null,
        shopAreaId: Long? = null,
        areaExplicit: Boolean = false,
    ): ItemModel {
        val id = tempIds.next()
        val entity = ItemEntity(
            id = id,
            listId = listId,
            name = name,
            quantity = quantity,
            unit = unit,
            shopAreaId = shopAreaId,
            checked = false,
            checkedBy = null,
            sortOrder = itemDao.maxSortOrder(listId) + 1,
            updatedAt = null,
        )
        itemDao.upsert(entity)
        enqueue(
            MutationTypes.CREATE, id, listId,
            json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload(name, quantity, unit, shopAreaId, areaExplicit)),
        )
        sync.requestSync()
        return entity.toModel()
    }

    suspend fun updateItem(
        item: ItemModel,
        name: String? = null,
        quantity: String? = null,
        shopAreaId: Long? = null,
        areaExplicit: Boolean? = null,
    ) {
        itemDao.getById(item.id)?.let { cur ->
            itemDao.update(
                cur.copy(
                    name = name ?: cur.name,
                    quantity = quantity ?: cur.quantity,
                    shopAreaId = shopAreaId ?: cur.shopAreaId,
                ),
            )
        }
        enqueue(
            MutationTypes.UPDATE, item.id, item.listId,
            json.encodeToString(ItemUpdatePayload.serializer(), ItemUpdatePayload(name = name, quantity = quantity, shopAreaId = shopAreaId, areaExplicit = areaExplicit)),
        )
        sync.requestSync()
    }

    suspend fun check(item: ItemModel, checked: Boolean) {
        itemDao.getById(item.id)?.let { cur ->
            itemDao.update(cur.copy(checked = checked, checkedBy = if (checked) cur.checkedBy else null))
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

    suspend fun clearChecked(listId: Long) {
        itemDao.deleteCheckedByList(listId)
        enqueue(MutationTypes.CLEAR_CHECKED, listId, listId, "{}")
        sync.requestSync()
    }

    suspend fun uncheckAll(listId: Long) {
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

    private suspend fun enqueue(type: String, targetId: Long, listId: Long, payload: String) =
        insertMutation(type, targetId, listId, payload)

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
