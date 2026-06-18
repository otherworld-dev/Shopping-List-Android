package dev.otherworld.shoppinglist.data.sync

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateListRequest
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateListRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the FIFO mutation queue to the server. Mutations reference local row ids; the real
 * server id is resolved at drain time. Creates run first (FIFO), so by the time a dependent
 * op runs the temp id has been remapped to the server id (in Room and in the queue).
 *
 * Error policy mirrors the web app: network errors stop the drain (retry on reconnect),
 * 404s are discarded (deleted elsewhere), other errors retry up to [MAX_ATTEMPTS] then discard.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val connectivity: ConnectivityObserver,
    private val json: Json,
) {
    private val itemDao = db.itemDao()
    private val listDao = db.listDao()
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val pendingCount get() = mutationDao.count()

    /** Fire-and-forget drain request. */
    fun requestSync() {
        scope.launch { drain() }
    }

    /** Drains the queue once. Returns when the queue is empty or a network error halts it. */
    suspend fun drain(): Boolean = mutex.withLock {
        if (!connectivity.isOnline.value) return false
        _syncing.value = true
        try {
            while (true) {
                val m = mutationDao.oldest() ?: break
                val outcome = runCatching { execute(m) }
                if (outcome.isSuccess) {
                    mutationDao.deleteBySeq(m.seq)
                    continue
                }
                when (val e = outcome.exceptionOrNull()) {
                    is IOException -> return true // network down — retry on reconnect
                    is HttpException -> {
                        if (e.code() == 404) {
                            mutationDao.deleteBySeq(m.seq) // gone on server — discard
                        } else {
                            val attempts = m.attempts + 1
                            if (attempts >= MAX_ATTEMPTS) {
                                mutationDao.deleteBySeq(m.seq)
                            } else {
                                mutationDao.update(m.copy(attempts = attempts))
                                return true // back off; retry later
                            }
                        }
                    }
                    else -> {
                        val attempts = m.attempts + 1
                        if (attempts >= MAX_ATTEMPTS) mutationDao.deleteBySeq(m.seq)
                        else { mutationDao.update(m.copy(attempts = attempts)); return true }
                    }
                }
            }
            true
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun execute(m: MutationEntity) {
        when (m.entity) {
            MutationEntities.ITEM -> executeItem(m)
            MutationEntities.LIST -> executeList(m)
        }
    }

    private suspend fun executeItem(m: MutationEntity) {
        when (m.type) {
            MutationTypes.CREATE -> {
                val p = json.decodeFromString<ItemCreatePayload>(m.payload)
                val created = service.createItem(
                    m.listId,
                    CreateItemRequest(p.name, p.quantity, p.unit, p.shopAreaId, p.areaExplicit),
                ).ocs.data
                remapItemId(tempId = m.targetId, realId = created.id, updatedAt = created.updatedAt)
                // An explicit area assignment makes the server learn this name -> area; pull the
                // updated keywords back so the next auto-detect picks them up immediately.
                if (p.areaExplicit) refreshAreas(m.listId)
            }
            MutationTypes.UPDATE -> {
                val p = json.decodeFromString<ItemUpdatePayload>(m.payload)
                service.updateItem(
                    m.listId, m.targetId,
                    UpdateItemRequest(p.name, p.quantity, p.unit, p.shopAreaId, p.sortOrder, p.areaExplicit),
                )
                if (p.areaExplicit == true) refreshAreas(m.listId)
            }
            MutationTypes.CHECK -> {
                val p = json.decodeFromString<CheckPayload>(m.payload)
                service.checkItem(m.listId, m.targetId, CheckRequest(p.checked))
            }
            MutationTypes.DELETE -> service.deleteItem(m.listId, m.targetId)
            MutationTypes.REORDER -> {
                val p = json.decodeFromString<ReorderPayload>(m.payload)
                service.reorder(m.listId, ReorderRequest(p.sortedIds))
            }
            MutationTypes.CLEAR_CHECKED -> service.clearChecked(m.listId)
            MutationTypes.UNCHECK_ALL -> service.uncheckAll(m.listId)
        }
    }

    private suspend fun executeList(m: MutationEntity) {
        when (m.type) {
            MutationTypes.CREATE -> {
                val p = json.decodeFromString<TitlePayload>(m.payload)
                val created = service.createList(CreateListRequest(p.title)).ocs.data
                remapListId(tempId = m.targetId, realId = created.id)
            }
            MutationTypes.RENAME -> {
                val p = json.decodeFromString<TitlePayload>(m.payload)
                service.updateList(m.targetId, UpdateListRequest(p.title))
            }
            MutationTypes.DELETE -> service.deleteList(m.targetId)
        }
    }

    /** Re-fetches a list's shop areas (e.g. after the server learned a new keyword). */
    private suspend fun refreshAreas(listId: Long) {
        if (listId <= 0) return
        val areas = runCatching { service.getAreas(listId).ocs.data }.getOrNull() ?: return
        db.withTransaction {
            areaDao.deleteByList(listId)
            areaDao.upsertAll(areas.map { it.toEntity(listId) })
        }
    }

    /** Swap a temp item id for the real server id across Room and the queue. */
    private suspend fun remapItemId(tempId: Long, realId: Long, updatedAt: String?) {
        if (tempId == realId) return
        db.withTransaction {
            val temp = itemDao.getById(tempId) ?: return@withTransaction
            itemDao.deleteById(tempId)
            itemDao.upsert(temp.copy(id = realId, updatedAt = updatedAt))
            mutationDao.remapTarget(MutationEntities.ITEM, tempId, realId)
            remapReorderIds(tempId, realId)
        }
    }

    /** Swap a temp list id for the real server id across Room and the queue. */
    private suspend fun remapListId(tempId: Long, realId: Long) {
        if (tempId == realId) return
        db.withTransaction {
            val temp = listDao.getById(tempId) ?: return@withTransaction
            listDao.deleteById(tempId)
            listDao.upsert(temp.copy(id = realId))
            itemDao.remapListId(tempId, realId)
            areaDao.remapListId(tempId, realId)
            mutationDao.remapTarget(MutationEntities.LIST, tempId, realId)
            mutationDao.remapListId(tempId, realId)
        }
    }

    /** Rewrite any queued reorder payloads that reference the old (temp) item id. */
    private suspend fun remapReorderIds(oldId: Long, newId: Long) {
        for (mutation in mutationDao.reorderMutations()) {
            val p = json.decodeFromString<ReorderPayload>(mutation.payload)
            if (oldId in p.sortedIds) {
                val updated = p.copy(sortedIds = p.sortedIds.map { if (it == oldId) newId else it })
                mutationDao.update(mutation.copy(payload = json.encodeToString(ReorderPayload.serializer(), updated)))
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
