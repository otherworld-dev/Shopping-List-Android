package dev.otherworld.shoppinglist.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.ItemImagesCaps
import dev.otherworld.shoppinglist.data.remote.dto.UpdateSettingsRequest
import dev.otherworld.shoppinglist.data.remote.dto.supportsGuestNames
import dev.otherworld.shoppinglist.data.remote.dto.supportsItemImages
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

    private val _imagesSupported = MutableStateFlow(prefs.getBoolean(KEY_IMAGES, false))

    /** Whether the server can keep a photo on an item (server app 1.9.0 and later). */
    val itemImagesSupported: StateFlow<Boolean> = _imagesSupported.asStateFlow()

    private val _guestNamesSupported = MutableStateFlow(prefs.getBoolean(KEY_NAMES, false))

    /** Whether the server keeps who added and ticked items (server app 1.10.0 and later). */
    val guestNamesSupported: StateFlow<Boolean> = _guestNamesSupported.asStateFlow()

    private val ownName = OptimisticSetting(prefs.getBoolean(KEY_SHOW_OWN_NAME, false)) {
        prefs.edit().putBoolean(KEY_SHOW_OWN_NAME, it).apply()
    }

    /** Show this user's own name on items they added or ticked; off by default, as on the web. */
    val showOwnName: StateFlow<Boolean> = ownName.value

    private val _ownUserId = MutableStateFlow(prefs.getString(KEY_USER_ID, null))

    /**
     * This user's Nextcloud user id, which items record as who added or ticked them; null until
     * the server has said. It can differ from the login (an email address, say), which is what
     * [ownUserIdOrLogin] falls back to meanwhile.
     */
    val ownUserId: StateFlow<String?> = _ownUserId.asStateFlow()

    fun ownUserIdOrLogin(id: String?): String? = id ?: credentialStore.current()?.loginName

    /** The largest photo the server takes, from its capabilities. */
    val maxUploadBytes: Long
        get() = prefs.getLong(KEY_MAX_UPLOAD, ItemImagesCaps.DEFAULT_MAX_UPLOAD_BYTES)

    /** What the server can do (list orders, photos), then this user's sort (see [refreshSort]). */
    suspend fun refresh() {
        val caps = service.capabilities().ocs.data.capabilities
        val supported = caps.supportsListOrder()
        val images = caps.supportsItemImages()
        val names = caps.supportsGuestNames()
        _supported.value = supported
        _imagesSupported.value = images
        _guestNamesSupported.value = names
        prefs.edit()
            .putBoolean(KEY_SUPPORTED, supported)
            .putBoolean(KEY_IMAGES, images)
            .putBoolean(KEY_NAMES, names)
            .putLong(
                KEY_MAX_UPLOAD,
                caps.shoppingList?.itemImages?.maxUploadBytes ?: ItemImagesCaps.DEFAULT_MAX_UPLOAD_BYTES,
            )
            .apply()
        if (!supported && !names) return
        // One settings fetch for the sort and the name switch, each guarded against a change the
        // user made while it was out.
        val (editsBefore, queuedBefore) = sortLock.withLock { sortEdits.get() to settingsQueued() }
        val nameToken = ownName.beginRefresh()
        val settings = service.getSettings().ocs.data
        if (supported) applySort(settings.listSort, editsBefore, queuedBefore)
        if (names) {
            ownName.applyRefresh(settings.showOwnName, nameToken)
            val id = service.currentUser().ocs.data.id.ifBlank { null }
            _ownUserId.value = id
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }
    }

    /**
     * Straight to the server, like the web app's switch: it's only how items look, so it isn't
     * queued. Shown at once; if the server can't be told it goes back and the failure is thrown.
     */
    suspend fun setShowOwnName(enabled: Boolean) {
        ownName.set(enabled) { service.updateSettings(UpdateSettingsRequest(showOwnName = it)) }
    }

    /**
     * This user's sort from the server, when it keeps list orders, unless a change of theirs is
     * newer than the response: still queued, or made while the fetch was in flight.
     */
    suspend fun refreshSort() {
        if (!_supported.value) return
        // Under the lock, so neither read lands between setListSort's local write and its queueing.
        val (editsBefore, queuedBefore) = sortLock.withLock { sortEdits.get() to settingsQueued() }
        applySort(service.getSettings().ocs.data.listSort, editsBefore, queuedBefore)
    }

    private suspend fun applySort(listSort: String?, editsBefore: Long, queuedBefore: Boolean) {
        sortLock.withLock {
            if (!localChangeWins(queuedBefore, editsBefore, sortEdits.get(), settingsQueued())) {
                remember(ListSortMode.fromStorage(listSort))
            }
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
        _imagesSupported.value = false
        _guestNamesSupported.value = false
        ownName.reset()
        _ownUserId.value = null
    }

    private suspend fun settingsQueued() = mutationDao.countByType(MutationTypes.UPDATE_SETTINGS) > 0

    private fun remember(mode: ListSortMode) {
        _listSort.value = mode
        prefs.edit().putString(KEY_SORT, mode.storageValue).apply()
    }

    private companion object {
        const val KEY_SORT = "list_sort"
        const val KEY_SUPPORTED = "list_order_supported"
        const val KEY_IMAGES = "item_images_supported"
        const val KEY_MAX_UPLOAD = "item_images_max_upload"
        const val KEY_NAMES = "guest_names_supported"
        const val KEY_SHOW_OWN_NAME = "show_own_name"
        const val KEY_USER_ID = "user_id"
    }
}
