package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CopyAreasRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateAreaRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateAreaRequest
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) {
    private val areaDao = db.areaDao()

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
        refresh(listId)
    }

    suspend fun copyAreas(listId: Long, sourceListId: Long) {
        service.copyAreas(listId, CopyAreasRequest(sourceListId))
        refresh(listId)
    }

    /**
     * Persists a new ordering of shop areas (drag-and-drop) so the item grouping follows the
     * user's store layout. Like other area edits this is online-direct: the new order is written
     * to Room at once for instant regrouping, then each moved area's sortOrder is pushed to the
     * server. Offline, the push fails and the order isn't persisted — a later refresh restores
     * the server's order, matching how the rest of area management behaves.
     */
    suspend fun reorderAreas(listId: Long, orderedIds: List<Long>) {
        val byId = areaDao.getByList(listId).associateBy { it.id }
        val reordered = orderedIds.mapIndexedNotNull { index, id -> byId[id]?.copy(sortOrder = index) }
        if (reordered.isEmpty()) return
        areaDao.upsertAll(reordered)
        reordered.forEach { area ->
            if (byId[area.id]?.sortOrder != area.sortOrder) {
                val dto = service.updateArea(listId, area.id, UpdateAreaRequest(sortOrder = area.sortOrder)).ocs.data
                areaDao.upsertAll(listOf(dto.toEntity(listId)))
            }
        }
    }

    private suspend fun refresh(listId: Long) {
        val areas = service.getAreas(listId).ocs.data
        areaDao.deleteByList(listId)
        areaDao.upsertAll(areas.map { it.toEntity(listId) })
    }
}
