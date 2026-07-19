package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CopyAreasRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateAreaRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateAreaRequest
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.ReorderPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shop-area management. Areas change rarely and deliberately, so mutations go straight to the
 * server (online) and the result is written into Room; the cached areas drive item grouping
 * and offline auto-detection.
 */
@Singleton
class AreaRepository @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val sync: SyncEngine,
    private val json: Json,
) {
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()

    fun observeAreas(listId: Long): Flow<List<ShopAreaModel>> =
        areaDao.observeByList(listId).map { rows -> rows.map { it.toModel() } }

    suspend fun createArea(listId: Long, name: String, color: String?, keywords: List<String>) {
        val dto = service.createArea(listId, CreateAreaRequest(name, color, keywords)).ocs.data
        areaDao.upsertAll(listOf(dto.toEntity(listId)))
    }

    suspend fun updateArea(
        listId: Long,
        id: Long,
        name: String? = null,
        color: String? = null,
        keywords: List<String>? = null,
    ) {
        val dto = service.updateArea(listId, id, UpdateAreaRequest(name, color, null, keywords)).ocs.data
        areaDao.upsertAll(listOf(dto.toEntity(listId)))
    }

    suspend fun deleteArea(listId: Long, id: Long) {
        service.deleteArea(listId, id)
        areaDao.deleteById(id) // remove locally now; refresh() is skipped while a reorder is pending
        refresh(listId)
        sync.requestSync() // let any pending reorder drain promptly
    }

    suspend fun copyAreas(listId: Long, sourceListId: Long) {
        val copied = service.copyAreas(listId, CopyAreasRequest(sourceListId)).ocs.data
        if (mutationDao.pendingAreaCount(listId) > 0) {
            // A pending reorder owns the local order, so a full refresh is skipped (it would clobber
            // it). Append only the genuinely-new copied areas after the current end so they still
            // show immediately; the server's authoritative order lands on the next refresh once the
            // reorder has synced. (copyAreas may return the full list or just the copies, so we
            // filter by what's already local rather than assuming.)
            db.withTransaction {
                val current = areaDao.getByList(listId)
                val existing = current.map { it.id }.toSet()
                var next = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val newAreas = copied.filterNot { it.id in existing }.map { dto ->
                    dto.toEntity(listId).copy(sortOrder = next++)
                }
                if (newAreas.isNotEmpty()) areaDao.upsertAll(newAreas)
            }
        } else {
            refresh(listId)
        }
        sync.requestSync()
    }

    /**
     * Persists a new ordering of shop areas (drag-and-drop) so the item grouping follows the
     * user's store layout. Unlike other area edits this is offline-capable — reordering happens
     * while shopping, so it goes through the mutation queue: the new order is written to Room at
     * once for instant regrouping and queued, then [SyncEngine] pushes each area's sortOrder when
     * online. A pending reorder is preserved across refreshes (see [ItemRepository]).
     */
    suspend fun reorderAreas(listId: Long, orderedIds: List<Long>) {
        var enqueued = false
        // Read and write in one transaction so a concurrent refresh can't slip an area in/out
        // between reading the current set and renumbering it.
        db.withTransaction {
            val current = areaDao.getByList(listId)
            if (current.isEmpty()) return@withTransaction
            val byId = current.associateBy { it.id }
            // Renumber ALL areas, not just the dragged subset: an area added on the server while
            // the sheet was open won't be in orderedIds — keep it, appended after the ordered ones
            // — so the final sortOrder is always a contiguous, collision-free 0..N-1.
            val ordered = orderedIds.distinct().mapNotNull { byId[it] }
            val orderedIdSet = ordered.map { it.id }.toSet()
            val rest = current.filterNot { it.id in orderedIdSet }
            val finalOrder = (ordered + rest).mapIndexed { index, area -> area.copy(sortOrder = index) }
            areaDao.upsertAll(finalOrder)
            mutationDao.deleteAreaReorders(listId) // a newer order supersedes any pending one
            mutationDao.insert(
                MutationEntity(
                    entity = MutationEntities.AREA,
                    type = MutationTypes.REORDER,
                    targetId = listId,
                    listId = listId,
                    payload = json.encodeToString(
                        ReorderPayload.serializer(),
                        ReorderPayload(finalOrder.map { it.id }),
                    ),
                ),
            )
            enqueued = true
        }
        if (enqueued) sync.requestSync()
    }

    private suspend fun refresh(listId: Long) {
        if (mutationDao.pendingAreaCount(listId) > 0) return // a pending reorder owns the local order
        val areas = service.getAreas(listId).ocs.data
        db.withTransaction {
            if (mutationDao.pendingAreaCount(listId) > 0) return@withTransaction
            areaDao.deleteByList(listId)
            areaDao.upsertAll(areas.map { it.toEntity(listId) })
        }
    }
}
