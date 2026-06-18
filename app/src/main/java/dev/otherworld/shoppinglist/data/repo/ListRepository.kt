package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.ListEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
import dev.otherworld.shoppinglist.data.sync.TitlePayload
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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

    fun observeLists(): Flow<List<ShoppingListModel>> =
        listDao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Fetches lists from the server and reconciles them into Room without clobbering pending edits. */
    suspend fun refresh() {
        val dtos = service.getLists().ocs.data
        val pending = mutationDao.pendingListIds().toSet()
        db.withTransaction {
            val serverIds = dtos.map { it.id }.toSet()
            val toDelete = listDao.allIds().filter { it > 0 && it !in serverIds && it !in pending }
            listDao.deleteByIds(toDelete)
            dtos.forEachIndexed { index, dto ->
                if (dto.id !in pending) listDao.upsert(dto.toEntity(index))
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
