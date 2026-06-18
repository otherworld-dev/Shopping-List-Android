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

    private suspend fun refresh(listId: Long) {
        val areas = service.getAreas(listId).ocs.data
        areaDao.deleteByList(listId)
        areaDao.upsertAll(areas.map { it.toEntity(listId) })
    }
}
