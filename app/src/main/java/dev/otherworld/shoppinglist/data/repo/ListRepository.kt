package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.ListEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.sync.ListOrderPayload
import dev.otherworld.shoppinglist.data.sync.localChangeWins
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.PinPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
import dev.otherworld.shoppinglist.data.sync.TitlePayload
import dev.otherworld.shoppinglist.data.sync.positionAfterRefresh
import dev.otherworld.shoppinglist.domain.guest.GuestIds
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** The user's own lists the server no longer has. Unsynced, still-queued and guest lists stay. */
internal fun ownListsGone(localIds: List<Long>, serverIds: Set<Long>, pending: Set<Long>): List<Long> =
    localIds.filter { it > 0 && !GuestIds.isGuest(it) && it !in serverIds && it !in pending }

@Singleton
class ListRepository @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val sync: SyncEngine,
    private val tempIds: TempIds,
    private val json: Json,
) {
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()

    /** Bumped by every local change to list positions, so a refresh can tell one happened mid-fetch. */
    private val orderEdits = AtomicLong()

    fun observeLists(): Flow<List<ShoppingListModel>> =
        listDao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Fetches lists from the server and reconciles them into Room without clobbering pending edits. */
    suspend fun refresh() {
        // A reorder queued before the fetch, or made while it's in flight (even one sent and
        // dequeued before the response), must not be overwritten by the response's stale
        // positions. Both reads are transactions so neither lands midway through a local edit.
        val (editsBefore, queuedBefore) = db.withTransaction { orderEdits.get() to reorderQueued() }
        val dtos = service.getLists().ocs.data
        db.withTransaction {
            val localOrderWins = localChangeWins(queuedBefore, editsBefore, orderEdits.get(), reorderQueued())
            val pending = mutationDao.pendingListIds().toSet()
            val serverIds = dtos.map { it.id }.toSet()
            val toDelete = ownListsGone(listDao.allIds(), serverIds, pending)
            listDao.deleteByIds(toDelete)
            dtos.forEachIndexed { index, dto ->
                if (dto.id !in pending) {
                    val entity = dto.toEntity(index)
                    val local = listDao.getById(dto.id)?.position
                    listDao.upsert(entity.copy(position = positionAfterRefresh(entity.position, local, localOrderWins)))
                }
            }
        }
        sync.requestSync()
    }

    /** Optimistically creates a list locally and queues it; returns the local id for navigation. */
    suspend fun createList(title: String): Long {
        val id = tempIds.next()
        listDao.upsert(
            ListEntity(
                id = id,
                title = title,
                permission = Permission.WRITE,
                isOwner = true,
                sortOrder = -1, // surface new lists at the top until next refresh
                updatedAt = null,
            ),
        )
        enqueue(MutationTypes.CREATE, id, json.encodeToString(TitlePayload.serializer(), TitlePayload(title)))
        sync.requestSync()
        return id
    }

    /** Pins or unpins a list for this user; offline-first, so it waits in the queue like a rename. */
    suspend fun setPinned(id: Long, isPinned: Boolean) {
        db.withTransaction {
            listDao.getById(id)?.let { listDao.update(it.copy(isPinned = isPinned, position = null)) }
            enqueue(MutationTypes.UPDATE_PREFERENCES, id, json.encodeToString(PinPayload.serializer(), PinPayload(isPinned)))
            orderEdits.incrementAndGet()
        }
        sync.requestSync()
    }

    /**
     * Saves one section's order for this user: at once on the phone, then queued, like a pin.
     * [listIds] may hold temp ids of lists not yet synced; the sync engine swaps in real ones.
     */
    suspend fun reorderLists(listIds: List<Long>) {
        db.withTransaction {
            listIds.forEachIndexed { index, id ->
                listDao.getById(id)?.let { listDao.update(it.copy(position = index)) }
            }
            mutationDao.insert(
                MutationEntity(
                    entity = MutationEntities.LIST,
                    type = MutationTypes.REORDER_LISTS,
                    targetId = 0,
                    listId = 0,
                    payload = json.encodeToString(ListOrderPayload.serializer(), ListOrderPayload(listIds)),
                ),
            )
            orderEdits.incrementAndGet()
        }
        sync.requestSync()
    }

    suspend fun renameList(id: Long, title: String) {
        listDao.getById(id)?.let { listDao.update(it.copy(title = title)) }
        enqueue(MutationTypes.RENAME, id, json.encodeToString(TitlePayload.serializer(), TitlePayload(title)))
        sync.requestSync()
    }

    suspend fun deleteList(id: Long) {
        db.withTransaction {
            listDao.deleteById(id)
            itemDao.deleteByList(id)
            areaDao.deleteByList(id)
            if (id < 0) {
                // Never reached the server — drop its queued ops instead of issuing a delete.
                mutationDao.deleteByTarget(MutationEntities.LIST, id)
            } else {
                insertMutation(MutationTypes.DELETE, id, "{}")
            }
        }
        sync.requestSync()
    }

    private suspend fun reorderQueued() = mutationDao.countByType(MutationTypes.REORDER_LISTS) > 0

    private suspend fun enqueue(type: String, targetId: Long, payload: String) =
        insertMutation(type, targetId, payload)

    private suspend fun insertMutation(type: String, targetId: Long, payload: String) {
        mutationDao.insert(
            MutationEntity(
                entity = MutationEntities.LIST,
                type = type,
                targetId = targetId,
                listId = targetId,
                payload = payload,
            ),
        )
    }
}
