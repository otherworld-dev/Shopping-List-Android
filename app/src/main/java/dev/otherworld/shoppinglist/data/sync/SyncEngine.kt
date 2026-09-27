package dev.otherworld.shoppinglist.data.sync

import android.os.SystemClock
import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.guest.GuestLinkDeadException
import dev.otherworld.shoppinglist.data.guest.GuestPasswordNeededException
import dev.otherworld.shoppinglist.data.guest.GuestReadOnlyException
import dev.otherworld.shoppinglist.data.guest.GuestSendResult
import dev.otherworld.shoppinglist.data.guest.GuestSender
import dev.otherworld.shoppinglist.data.guest.GuestServerTroubleException
import dev.otherworld.shoppinglist.data.guest.GuestShareMarks
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.GuestShareState
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateListRequest
import dev.otherworld.shoppinglist.data.remote.dto.ListPreferencesRequest
import dev.otherworld.shoppinglist.data.remote.dto.ReorderListsRequest
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateAreaRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateListRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateSettingsRequest
import dev.otherworld.shoppinglist.domain.text.SmartInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the FIFO mutation queue to the server. Mutations reference local row ids; the real
 * server id is resolved at drain time. Creates run first (FIFO), so by the time a dependent
 * op runs the temp id has been remapped to the server id (in Room and in the queue).
 *
 * Each change goes to a [Destination]: the user's own server, or the server behind one share
 * link. Changes keep their order within a destination, and a failure pauses only its own
 * destination for the rest of the drain, so one server being down never holds up another.
 *
 * Error policy (see [SyncErrorPolicy]): network errors pause the destination (retry on
 * reconnect); 404s are discarded (deleted elsewhere); server errors (5xx, 429) are transient and
 * retry WITHOUT burning an attempt, so a restarting server never costs queued changes; only
 * genuine rejections count toward [MAX_ATTEMPTS] before being discarded. A [SyncBackoff]
 * cooldown per destination spaces failed drains out, so a burst of requestSync calls (a
 * multi-line paste) can't exhaust a mutation's attempts inside a second.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val connectivity: ConnectivityObserver,
    private val json: Json,
    private val smartInput: SmartInput,
    private val guestSender: GuestSender,
    private val shareMarks: GuestShareMarks,
    private val credentialStore: CredentialStore,
) {
    private val itemDao = db.itemDao()
    private val listDao = db.listDao()
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()
    private val shareDao = db.guestShareDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val backoffs = mutableMapOf<Destination, SyncBackoff>()
    private fun backoff(d: Destination) = backoffs.getOrPut(d) { SyncBackoff() }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // Emits when a mutation is given up on (dropped after max retries) — a durable, user-visible
    // sync failure the UI should surface, unlike transient retryable errors.
    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    val pendingCount get() = mutationDao.count()

    /** Fire-and-forget drain request. */
    fun requestSync() {
        scope.launch { drain() }
    }

    /**
     * Drains the queue once. A destination that fails (offline, down, rate limited, waiting for a
     * password) is skipped for the rest of this drain while the others carry on. Returns true only
     * when the queue fully drained; [SyncWorker] schedules a retry otherwise.
     */
    suspend fun drain(): Boolean = mutex.withLock {
        if (!connectivity.isOnline.value) return false
        _syncing.value = true
        try {
            val now = SystemClock.elapsedRealtime()
            val blocked = mutableSetOf<Destination>()
            if (credentialStore.current() == null) blocked += Destination.Own
            shareDao.all().filter { it.state != GuestShareState.OK }.forEach { blocked += Destination.Guest(it.id) }
            backoffs.forEach { (d, b) -> if (!b.isReady(now)) blocked += d }
            while (true) {
                val guestListShares = listDao.guestLists().associate { it.id to it.guestShareId }
                val m = firstRunnable(mutationDao.all(), { destinationOf(it, guestListShares) }, blocked) ?: break
                when (val d = destinationOf(m, guestListShares)) {
                    Destination.Orphan -> mutationDao.deleteBySeq(m.seq)
                    else -> if (!sendOne(m, d)) blocked += d
                }
            }
            mutationDao.oldest() == null
        } finally {
            _syncing.value = false
        }
    }

    /** Sends one change; false when its destination must wait for the rest of this drain. */
    private suspend fun sendOne(m: MutationEntity, d: Destination): Boolean {
        val outcome = runCatching {
            if (d is Destination.Guest) executeGuest(m, d.shareId) else { execute(m); false }
        }
        val now = SystemClock.elapsedRealtime()
        if (outcome.isSuccess) {
            backoff(d).recordSuccess()
            if (!outcome.getOrThrow()) mutationDao.deleteBySeq(m.seq)
            return true
        }
        when (val e = outcome.exceptionOrNull()) {
            is GuestLinkDeadException -> { shareMarks.dead(e.shareId); return false }
            is GuestReadOnlyException -> { shareMarks.readOnly(e.shareId); return false }
            is GuestPasswordNeededException -> { shareMarks.passwordNeeded(e.shareId); return false }
            // A 404 that isn't the app's own is the friend's server misbehaving, not a lost link.
            is GuestServerTroubleException -> { backoff(d).recordFailure(now); return false }
        }
        return when (SyncErrorPolicy.classify(outcome.exceptionOrNull())) {
            SyncErrorAction.HALT -> {
                // Network down: retry on reconnect. An unreachable friend's server can take up to
                // a minute to time out, so it cools down rather than stalling every drain.
                if (d is Destination.Guest) backoff(d).recordFailure(now)
                false
            }
            SyncErrorAction.DISCARD -> { mutationDao.deleteBySeq(m.seq); true } // gone on server — benign
            // Server trouble, not this mutation's fault: cool down with the attempt count untouched.
            SyncErrorAction.TRANSIENT -> { backoff(d).recordFailure(now); false }
            SyncErrorAction.COUNT_ATTEMPT -> {
                backoff(d).recordFailure(now)
                val attempts = m.attempts + 1
                if (attempts >= MAX_ATTEMPTS) {
                    mutationDao.deleteBySeq(m.seq)
                    _failures.tryEmit(Unit) // gave up — surface it
                    true
                } else {
                    mutationDao.update(m.copy(attempts = attempts))
                    false
                }
            }
        }
    }

    /**
     * Sends one guest change. True when its queue row was kept: a ticked create's row becomes the
     * tick (same seq, so it goes next), which then retries alone and never re-creates the item.
     */
    private suspend fun executeGuest(m: MutationEntity, shareId: Long): Boolean {
        val share = shareDao.getById(shareId) ?: return false
        val localAreas = areaDao.getByList(m.listId).map { it.toModel() }
        val result = guestSender.send(m, share, localAreas) as? GuestSendResult.Created ?: return false
        result.detectedAreaId?.let { area ->
            itemDao.getById(m.targetId)?.let { row -> if (row.shopAreaId == null) itemDao.upsert(row.copy(shopAreaId = area)) }
        }
        return db.withTransaction {
            remapItemId(tempId = m.targetId, realId = result.localId, updatedAt = result.updatedAt)
            if (!result.tickPending) return@withTransaction false
            // Re-read: the remap has already rewritten this row's targetId.
            val row = mutationDao.getBySeq(m.seq) ?: return@withTransaction false
            mutationDao.update(
                row.copy(
                    type = MutationTypes.CHECK,
                    targetId = result.localId,
                    payload = json.encodeToString(CheckPayload.serializer(), CheckPayload(true)),
                    attempts = 0,
                ),
            )
            true
        }
    }

    private suspend fun execute(m: MutationEntity) {
        when (m.entity) {
            MutationEntities.ITEM -> executeItem(m)
            MutationEntities.LIST -> executeList(m)
            MutationEntities.AREA -> executeArea(m)
        }
    }

    private suspend fun executeArea(m: MutationEntity) {
        when (m.type) {
            MutationTypes.REORDER -> {
                // No bulk area-reorder endpoint: push each area's sortOrder. Re-running the whole
                // loop on retry is idempotent. Areas deleted on the server (404) are skipped; other
                // failures propagate so drain halts/retries.
                val p = json.decodeFromString<ReorderPayload>(m.payload)
                p.sortedIds.forEachIndexed { index, areaId ->
                    runCatching { service.updateArea(m.listId, areaId, UpdateAreaRequest(sortOrder = index)) }
                        .onFailure { e -> if (e !is HttpException || e.code() != 404) throw e }
                }
            }
        }
    }

    private suspend fun executeItem(m: MutationEntity) {
        when (m.type) {
            MutationTypes.CREATE -> {
                val p = json.decodeFromString<ItemCreatePayload>(m.payload)
                val areaId = if (p.detectArea) detectQueuedArea(m, p) else p.shopAreaId
                val created = service.createItem(
                    m.listId,
                    CreateItemRequest(p.name, p.quantity, p.unit, areaId, p.areaExplicit, p.checked),
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
            MutationTypes.UPDATE_PREFERENCES -> {
                val p = json.decodeFromString<PinPayload>(m.payload)
                service.updateListPreferences(m.targetId, ListPreferencesRequest(p.isPinned))
            }
            MutationTypes.REORDER_LISTS -> {
                val ids = json.decodeFromString<ListOrderPayload>(m.payload).idsToSend()
                if (ids.isNotEmpty()) service.reorderLists(ReorderListsRequest(ids))
            }
            MutationTypes.UPDATE_SETTINGS -> {
                val p = json.decodeFromString<SettingsPayload>(m.payload)
                service.updateSettings(UpdateSettingsRequest(listSort = p.listSort))
            }
            MutationTypes.DELETE -> service.deleteList(m.targetId)
        }
    }

    /**
     * Detects the area for an item that was added before its list's areas reached the phone,
     * fetching them first if they still haven't. The local row takes the area too, unless the
     * user has already given it one.
     */
    private suspend fun detectQueuedArea(m: MutationEntity, p: ItemCreatePayload): Long? {
        if (areaDao.getByList(m.listId).isEmpty()) refreshAreas(m.listId)
        val areas = areaDao.getByList(m.listId).map { it.toModel() }
        val areaId = areaForQueuedCreate(p, areas, smartInput) ?: return null
        itemDao.getById(m.targetId)?.let { row ->
            if (row.shopAreaId == null) itemDao.upsert(row.copy(shopAreaId = areaId))
        }
        return areaId
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
            remapListOrderIds(tempId, realId)
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

    /** Rewrite queued list orders that still hold a list's temp id. */
    private suspend fun remapListOrderIds(oldId: Long, newId: Long) {
        for (mutation in mutationDao.byType(MutationTypes.REORDER_LISTS)) {
            val updated = json.decodeFromString<ListOrderPayload>(mutation.payload).remapped(oldId, newId) ?: continue
            mutationDao.update(mutation.copy(payload = json.encodeToString(ListOrderPayload.serializer(), updated)))
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
