package dev.otherworld.shoppinglist.ui.items

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.guest.GuestIdStore
import dev.otherworld.shoppinglist.data.guest.GuestRepository
import dev.otherworld.shoppinglist.data.photo.ItemPhotoUrls
import dev.otherworld.shoppinglist.data.photo.PhotoSize
import dev.otherworld.shoppinglist.data.photo.PhotoUrls
import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.local.GuestShareState
import dev.otherworld.shoppinglist.data.prefs.Density
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.repo.AreaRepository
import dev.otherworld.shoppinglist.data.repo.ItemRepository
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.repo.ListSettingsRepository
import dev.otherworld.shoppinglist.data.repo.PhotoRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.guest.GuestIds
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.OpenSort
import dev.otherworld.shoppinglist.domain.text.SmartInput
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Photos on this list's items: whether they show, whether this user can change them, and where they are. */
data class PhotoUiState(
    val show: Boolean = false,
    val canEdit: Boolean = false,
    /** Item id to its photo's addresses, for the items that have one. */
    val urls: Map<Long, ItemPhotoUrls> = emptyMap(),
    /** Items whose photo is being added or removed right now. */
    val busy: Set<Long> = emptySet(),
)

data class ItemsUiState(
    val listId: Long = 0,
    val title: String = "",
    val canWrite: Boolean = true,
    val loading: Boolean = false,
    val items: List<ItemModel> = emptyList(),
    val areas: List<ShopAreaModel> = emptyList(),
    /** Writable other lists — targets for "Move to list". */
    val otherLists: List<ShoppingListModel> = emptyList(),
    val error: UiText? = null,
    val notice: UiText? = null,
    val density: Density = Density.COMFY,
    val openSort: OpenSort = OpenSort.AREA,
    val boughtSort: BoughtSort = BoughtSort.AREA,
    /** Folded area groups on this list, as [dev.otherworld.shoppinglist.domain.sort.CollapsedAreas] keys. */
    val collapsedAreas: Set<String> = emptySet(),
    val isGuest: Boolean = false,
    /** Kept on this phone only, so no areas, sharing or moves. */
    val isLocal: Boolean = false,
    /** GuestShareState of the link a guest list came from; null for the user's own lists. */
    val guestState: String? = null,
    val droppedChanges: Int = 0,
)

@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val areaRepository: AreaRepository,
    listRepository: ListRepository,
    private val smartInput: SmartInput,
    private val connectivity: ConnectivityObserver,
    private val realtime: RealtimeController,
    private val syncEngine: SyncEngine,
    private val displayPrefs: DisplayPrefs,
    private val guests: GuestRepository,
    private val guestIds: GuestIdStore,
    private val photoRepository: PhotoRepository,
    listSettings: ListSettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L
    private val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val canWrite: Boolean = savedStateHandle.get<Boolean>("canWrite") ?: true
    private val isGuest = GuestIds.isGuest(listId)

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<UiText?>(null)
    private val _notice = MutableStateFlow<UiText?>(null)

    val state: StateFlow<ItemsUiState> = combine(
        repository.observeItems(listId),
        repository.observeAreas(listId),
        listRepository.observeLists(),
        _loading,
        _error,
        _notice,
        displayPrefs.density,
        displayPrefs.openSort,
        displayPrefs.boughtSort,
        displayPrefs.collapsedAreas(listId),
        guests.observeShareForList(listId),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val share = values[10] as GuestShareEntity?
        @Suppress("UNCHECKED_CAST")
        val lists = values[2] as List<ShoppingListModel>
        val isLocal = lists.firstOrNull { it.id == listId }?.isLocal == true
        ItemsUiState(
            listId = listId,
            title = share?.title ?: title,
            // A guest list's permission can change under it (the owner makes the link view only).
            canWrite = if (share != null) share.permission >= Permission.WRITE && share.state != GuestShareState.DEAD else canWrite,
            loading = values[3] as Boolean,
            items = values[0] as List<ItemModel>,
            areas = values[1] as List<ShopAreaModel>,
            // Writable other lists as move targets, sorted by title (matches the web app). A move
            // is a server call, so none from a list kept on the phone.
            otherLists = if (isGuest || isLocal) emptyList() else lists
                .filter { it.id != listId && it.id > 0 && !it.isGuest && (it.isOwner || it.canWrite) }
                .sortedBy { it.title.lowercase() },
            error = values[4] as UiText?,
            notice = values[5] as UiText?,
            density = values[6] as Density,
            openSort = values[7] as OpenSort,
            boughtSort = values[8] as BoughtSort,
            collapsedAreas = values[9] as Set<String>,
            isGuest = isGuest,
            isLocal = isLocal,
            guestState = share?.state,
            droppedChanges = share?.droppedChanges ?: 0,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ItemsUiState(
            listId = listId,
            title = title,
            canWrite = canWrite,
            loading = true,
            density = displayPrefs.density.value,
            isGuest = isGuest,
        ),
    )

    private val _photoBusy = MutableStateFlow<Set<Long>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val photoUrls = combine(repository.observeItems(listId), guests.observeShareForList(listId)) { items, share -> items to share }
        .mapLatest { (items, share) -> photoUrlsFor(items, share) }

    val photos: StateFlow<PhotoUiState> = combine(
        displayPrefs.showImages,
        listSettings.itemImagesSupported,
        state,
        photoUrls,
        _photoBusy,
    ) { show, supported, st, urls, busy ->
        PhotoUiState(
            show = show,
            // A share link can't add or remove photos, and a list on the phone has no server to keep them.
            canEdit = show && supported && st.canWrite && !st.isGuest && !st.isLocal,
            urls = if (show) urls else emptyMap(),
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoUiState())

    init {
        refresh()
        // Push events come from the user's own server, which knows nothing of a guest list.
        if (!isGuest) {
            realtime.ensureConnected()
            viewModelScope.launch { realtime.events.collect { poll() } }
        }
        // Surface durable background-sync failures (a queued mutation was given up on).
        viewModelScope.launch {
            syncEngine.failures.collect { _error.value = UiText(R.string.error_sync_failed) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repository.refresh(listId)
            } catch (e: Exception) {
                _error.value = errorText(e)
            } finally {
                _loading.value = false
            }
        }
    }

    /** Silent background refresh used by the polling loop; no-ops while offline. */
    fun poll() {
        if (!connectivity.isOnline.value) return
        viewModelScope.launch {
            runCatching { repository.refresh(listId) }
        }
    }

    /**
     * Adds [rawInput] with the web app's smart behaviour: parse quantity out of the text,
     * auto-detect a shop area (unless [explicitAreaId] is set), and merge into an existing
     * unchecked item of the same name instead of creating a duplicate.
     *
     * Multi-line input (a pasted list) becomes one item per non-blank line, processed in order
     * so a later line merges into an item an earlier line just created — the same as the web
     * app's paste handler, which awaits each create before the next.
     */
    fun addItem(rawInput: String, explicitAreaId: Long?) {
        val lines = SmartInput.splitLines(rawInput)
        if (lines.isEmpty()) return
        val current = state.value
        viewModelScope.launch {
            // Room's flow won't have re-emitted between lines, so track the batch's own
            // creates/merges locally for duplicate detection.
            var existing = current.items
            for (line in lines) {
                when (val plan = smartInput.planAdd(line, current.areas, existing, explicitAreaId)) {
                    null -> Unit
                    is SmartInput.AddPlan.Create -> {
                        val created = repository.createItem(
                            listId = listId,
                            name = plan.name,
                            quantity = plan.quantity,
                            shopAreaId = plan.shopAreaId,
                            areaExplicit = plan.areaExplicit,
                            checked = plan.checked,
                            // With no areas known yet there was nothing to detect against.
                            detectAreaOnSync = current.areas.isEmpty(),
                        )
                        existing = existing + created
                    }
                    is SmartInput.AddPlan.Merge -> {
                        repository.updateItem(plan.target, name = plan.newName, quantity = plan.quantity)
                        existing = existing.map {
                            if (it.id == plan.target.id) it.copy(name = plan.newName ?: it.name, quantity = plan.quantity) else it
                        }
                    }
                }
            }
        }
    }

    fun toggleCheck(item: ItemModel) {
        viewModelScope.launch { repository.check(item, !item.checked) }
    }

    fun editItem(item: ItemModel, name: String, quantity: String?, shopAreaId: Long?) {
        viewModelScope.launch {
            repository.updateItem(
                item,
                name = name.trim(),
                quantity = quantity?.trim()?.ifBlank { null },
                shopAreaId = shopAreaId,
                areaExplicit = if (shopAreaId != null) true else null,
            )
        }
    }

    fun deleteItem(item: ItemModel) {
        viewModelScope.launch { repository.deleteItem(item) }
    }

    /**
     * Moves [item] to [target]. Online-direct like the web app — a cross-list move can't be
     * queued offline, so the offline and not-yet-synced cases are refused with an explanation.
     */
    fun moveItem(item: ItemModel, target: ShoppingListModel) {
        if (item.id < 0) {
            _error.value = UiText(R.string.error_item_not_synced)
            return
        }
        if (!connectivity.isOnline.value) {
            _error.value = UiText(R.string.error_move_offline)
            return
        }
        viewModelScope.launch {
            runCatching { repository.moveItem(item, target.id) }
                .onSuccess { _notice.value = UiText(R.string.notice_item_moved, item.name, target.title) }
                .onFailure { _error.value = UiText(R.string.error_move_failed) }
        }
    }

    /** A file for the camera app to save a new photo into. */
    fun newCameraTarget(): Uri = photoRepository.newCameraTarget()

    /** The camera came back without a picture. */
    fun cameraCancelled(target: Uri) = photoRepository.discard(target)

    /**
     * Attaches the picture at [source] to [item]. Online-direct like a move, so the offline and
     * not-yet-synced cases are refused with an explanation; it carries on if the list is left.
     */
    fun attachPhoto(item: ItemModel, source: Uri) {
        if (!photoChangeAllowed(item)) {
            photoRepository.discard(source)
            return
        }
        changePhoto(item, ::photoUploadErrorText) {
            try {
                photoRepository.attach(item, source)
            } finally {
                photoRepository.discard(source)
            }
        }
    }

    fun removePhoto(item: ItemModel) {
        if (!photoChangeAllowed(item)) return
        changePhoto(item, ::photoRemoveErrorText) { photoRepository.remove(item) }
    }

    private fun photoChangeAllowed(item: ItemModel): Boolean {
        val refusal = when {
            item.id < 0 -> R.string.error_item_not_synced
            !connectivity.isOnline.value -> R.string.error_photo_offline
            else -> return true
        }
        _error.value = UiText(refusal)
        return false
    }

    private fun changePhoto(item: ItemModel, describe: (Throwable) -> UiText, change: suspend () -> Unit) {
        if (item.id in _photoBusy.value) return
        _photoBusy.update { it + item.id }
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    change()
                } catch (e: Exception) {
                    _error.value = describe(e)
                    if (isItemGone(e)) runCatching { repository.refresh(listId) }
                } finally {
                    _photoBusy.update { it - item.id }
                }
            }
        }
    }

    /** Where each item's photo is: the user's own server, or the share link's for a guest list. */
    private suspend fun photoUrlsFor(items: List<ItemModel>, share: GuestShareEntity?): Map<Long, ItemPhotoUrls> =
        items.mapNotNull { item ->
            val key = item.imageKey ?: return@mapNotNull null
            val urls = if (isGuest) {
                val link = share ?: return@mapNotNull null
                val remote = guestIds.remoteId(item.id) ?: return@mapNotNull null
                ItemPhotoUrls(
                    thumbnail = PhotoUrls.public(link.server, link.token, remote, key, PhotoSize.THUMBNAIL) ?: return@mapNotNull null,
                    full = PhotoUrls.public(link.server, link.token, remote, key, PhotoSize.FULL) ?: return@mapNotNull null,
                )
            } else {
                // A row not yet on the server has no address there.
                if (item.id <= 0) return@mapNotNull null
                ItemPhotoUrls(
                    thumbnail = PhotoUrls.own(item.listId, item.id, key, PhotoSize.THUMBNAIL) ?: return@mapNotNull null,
                    full = PhotoUrls.own(item.listId, item.id, key, PhotoSize.FULL) ?: return@mapNotNull null,
                )
            }
            item.id to urls
        }.toMap()

    /** Persists a new ordering of item ids (from drag-and-drop). */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorder(listId, orderedIds) }
    }

    /** Reassigns an item to [areaId] (e.g. when dragged into another area's group). */
    fun moveToArea(item: ItemModel, areaId: Long?) {
        if (item.shopAreaId == areaId || areaId == null) return
        viewModelScope.launch { repository.updateItem(item, shopAreaId = areaId, areaExplicit = true) }
    }

    /** Persists a new ordering of the shop-area groups (drag-and-drop). */
    fun reorderAreas(orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching { areaRepository.reorderAreas(listId, orderedIds) }
                .onFailure { _error.value = errorText(it) }
        }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.clearChecked(listId) }
    }

    fun uncheckAll() {
        viewModelScope.launch { repository.uncheckAll(listId) }
    }

    /** Flips the item list between comfy and compact row spacing (persists locally). */
    fun toggleDensity() = displayPrefs.toggleDensity()

    /** Orders the open items (persists locally, one choice for all lists — like the web app). */
    fun setOpenSort(sort: OpenSort) = displayPrefs.setOpenSort(sort)

    /** Orders the checked-off section (persists locally, one choice for all lists). */
    fun setBoughtSort(sort: BoughtSort) = displayPrefs.setBoughtSort(sort)

    /** Folds or unfolds an area group on this list (persists locally, per list). */
    fun toggleCollapsed(areaId: Long?) = displayPrefs.toggleCollapsed(listId, areaId)

    fun consumeError() = _error.update { null }

    fun consumeNotice() = _notice.update { null }

    fun clearDropped() {
        viewModelScope.launch { guests.observeShareForList(listId).first()?.let { guests.clearDropped(it.id) } }
    }

    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            guests.observeShareForList(listId).first()?.let { guests.leave(it.id) }
            onLeft()
        }
    }

    /** The list's share link, for opening it again to enter a changed password. */
    fun passwordLink(onLink: (String) -> Unit) {
        viewModelScope.launch { guests.linkFor(listId)?.let(onLink) }
    }
}
