package dev.otherworld.shoppinglist.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.auth.LocalMode
import dev.otherworld.shoppinglist.data.guest.GuestRepository
import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.repo.ListOrdering
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.repo.ListSettingsRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.domain.guest.serverLabel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.ListSections
import dev.otherworld.shoppinglist.domain.sort.SectionKey
import dev.otherworld.shoppinglist.domain.sort.sortLists
import dev.otherworld.shoppinglist.domain.sort.splitLists
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ListsUiState(
    val loading: Boolean = false,
    val lists: List<ShoppingListModel> = emptyList(),
    val sections: ListSections = ListSections(emptyList(), emptyList(), emptyList()),
    /** Dragging needs a server that keeps list orders. */
    val canReorder: Boolean = false,
    /** Lists opened from share links, A to Z. */
    val guestLists: List<GuestListEntry> = emptyList(),
    val loggedIn: Boolean = false,
    /** Logged in, or using the app without an account. */
    val canCreate: Boolean = false,
    val error: UiText? = null,
)

/**
 * The user's own lists to show. Logged out, only the lists kept on this phone: an old account's
 * lists stay in Room with their queued changes, waiting for that login, but aren't shown.
 */
internal fun ownListsShown(lists: List<ShoppingListModel>, loggedIn: Boolean): List<ShoppingListModel> =
    lists.filter { !it.isGuest && (loggedIn || it.isLocal) }

private data class ListSources(
    val lists: List<ShoppingListModel>,
    val shares: List<GuestShareEntity>,
    val loggedIn: Boolean,
    val localMode: Boolean,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: ListRepository,
    private val credentialStore: CredentialStore,
    private val connectivity: ConnectivityObserver,
    private val listSettings: ListSettingsRepository,
    private val ordering: ListOrdering,
    private val guests: GuestRepository,
    localMode: LocalMode,
    realtime: RealtimeController,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<UiText?>(null)

    val accountLabel: StateFlow<String> = credentialStore.accountFlow
        .map { a -> a?.let { "${it.loginName} · ${it.server.removePrefix("https://").removePrefix("http://")}" } ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val listsAndGuests = combine(
        repository.observeLists(), guests.observeShares(), credentialStore.accountFlow, localMode.enabled,
    ) { lists, shares, account, local -> ListSources(lists, shares, account != null, local) }

    val state: StateFlow<ListsUiState> =
        combine(
            listsAndGuests, listSettings.listSort, listSettings.listOrderSupported, _loading, _error,
        ) { (lists, shares, loggedIn, localMode), sort, supported, loading, error ->
            val own = ownListsShown(lists, loggedIn)
            // With no account the phone's lists sort and reorder on the phone alone.
            val sortable = supported || !loggedIn
            val shareById = shares.associateBy { it.id }
            val collator = java.text.Collator.getInstance(Locale.getDefault())
            val guestLists = lists.filter { it.isGuest }
                .mapNotNull { list ->
                    shareById[list.guestShareId]?.let { share ->
                        GuestListEntry(list, serverLabel(share.server), share.state)
                    }
                }
                .sortedWith { a, b -> collator.compare(a.list.title, b.list.title) }
            ListsUiState(
                loading = loading,
                lists = own + guestLists.map { it.list },
                sections = if (sortable) sortLists(own, sort, Locale.getDefault()) else splitLists(own),
                guestLists = guestLists,
                loggedIn = loggedIn,
                canCreate = loggedIn || localMode,
                canReorder = sortable,
                error = error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState(loading = true))

    init {
        refresh()
        // Refresh instantly when the server pushes a change.
        realtime.events.onEach { poll() }.launchIn(viewModelScope)
        // Logging in from guest mode returns to this same screen: fetch the new account's lists.
        credentialStore.accountFlow.drop(1).onEach { if (it != null) refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (credentialStore.current() != null) {
                    repository.refresh()
                    runCatching { listSettings.refresh() }
                }
            } catch (e: Exception) {
                _error.value = errorText(e)
            } finally {
                quietly { guests.refreshShares(force = true) }
                _loading.value = false
            }
        }
    }

    /** Silent background refresh used by the polling loop; no-ops while offline. */
    fun poll() {
        if (!connectivity.isOnline.value) return
        viewModelScope.launch {
            if (credentialStore.current() != null) {
                quietly { repository.refresh() }
                quietly { listSettings.refreshSort() }
            }
            quietly { guests.refreshShares(force = false) }
        }
    }

    fun leave(shareId: Long) {
        viewModelScope.launch { guests.leave(shareId) }
    }

    /** runCatching for a background refresh, without swallowing the coroutine's cancellation. */
    private inline fun quietly(block: () -> Unit) {
        runCatching(block).onFailure { if (it is CancellationException) throw it }
    }

    fun createList(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.createList(title.trim()) }
    }

    fun renameList(id: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.renameList(id, title.trim()) }
    }

    fun deleteList(id: Long) {
        viewModelScope.launch { repository.deleteList(id) }
    }

    fun uploadList(id: Long) {
        viewModelScope.launch { repository.uploadLocal(id) }
    }

    fun setPinned(list: ShoppingListModel, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(list.id, pinned) }
    }

    /**
     * [onDone] runs after the save finishes (success or failure) so the screen can hold the
     * dragged row in place until then, rather than flashing back to the pre-drop order while a
     * sort switch to Custom is still in flight.
     */
    fun reorderSection(key: SectionKey, order: List<Long>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                ordering.reorderSection(key, order)
            } finally {
                onDone()
            }
        }
    }

    fun logout() = credentialStore.clear()

    fun consumeError() = _error.update { null }
}
