package dev.otherworld.shoppinglist.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.supportsListOrder
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.SettingsPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.localChangeWins
import dev.otherworld.shoppinglist.domain.sort.ListSortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How this user sorts their lists, kept on the server so it matches the web app, and cached
 * here so the lists screen sorts straight away, offline too. Changes are queued like a pin.
 */
@Singleton
class ListSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val service: OcsService,
    db: AppDatabase,
    private val sync: SyncEngine,
    private val json: Json,
    private val credentialStore: CredentialStore,
) {
    private val prefs = context.getSharedPreferences("list_settings", Context.MODE_PRIVATE)
    private val mutationDao = db.mutationDao()

    /** Bumped by every local sort change, so a refresh can tell one happened mid-fetch. */
    private val sortEdits = AtomicLong()
    private val sortLock = Mutex()

    private val _listSort = MutableStateFlow(ListSortMode.fromStorage(prefs.getString(KEY_SORT, null)))
    val listSort: StateFlow<ListSortMode> = _listSort.asStateFlow()

    private val _supported = MutableStateFlow(prefs.getBoolean(KEY_SUPPORTED, false))
    val listOrderSupported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Whether the server keeps list orders, then this user's sort (see [refreshSort]). */
    suspend fun refresh() {
        val supported = service.capabilities().ocs.data.capabilities.supportsListOrder()
        _supported.value = supported
        prefs.edit().putBoolean(KEY_SUPPORTED, supported).apply()
        refreshSort()
    }

    /**
     * This user's sort from the server, when it keeps list orders, unless a change of theirs is
     * newer than the response: still queued, or made while the fetch was in flight.
     */
    suspend fun refreshSort() {
        if (!_supported.value) return
        // Under the lock, so neither read lands between setListSort's local write and its queueing.
        val (editsBefore, queuedBefore) = sortLock.withLock { sortEdits.get() to settingsQueued() }
        val server = ListSortMode.fromStorage(service.getSettings().ocs.data.listSort)
        sortLock.withLock {
            if (!localChangeWins(queuedBefore, editsBefore, sortEdits.get(), settingsQueued())) remember(server)
        }
    }

    /** With no account the sort only sorts this phone's lists, so it stays on the phone. */
    suspend fun setListSort(mode: ListSortMode) {
        sortLock.withLock {
            remember(mode)
            if (credentialStore.current() == null) return
            mutationDao.insert(
                MutationEntity(
                    entity = MutationEntities.LIST,
                    type = MutationTypes.UPDATE_SETTINGS,
                    targetId = 0,
                    listId = 0,
                    payload = json.encodeToString(SettingsPayload.serializer(), SettingsPayload(mode.storageValue)),
                ),
            )
            sortEdits.incrementAndGet()
        }
        sync.requestSync()
    }

    /** On logout, so the next account starts from the defaults. */
    fun clear() {
        prefs.edit().clear().apply()
        _listSort.value = ListSortMode.UPDATED
        _supported.value = false
    }

    private suspend fun settingsQueued() = mutationDao.countByType(MutationTypes.UPDATE_SETTINGS) > 0

    private fun remember(mode: ListSortMode) {
        _listSort.value = mode
        prefs.edit().putString(KEY_SORT, mode.storageValue).apply()
    }

    private companion object {
        const val KEY_SORT = "list_sort"
        const val KEY_SUPPORTED = "list_order_supported"
    }
}
