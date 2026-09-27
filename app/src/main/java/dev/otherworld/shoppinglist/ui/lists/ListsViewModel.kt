package dev.otherworld.shoppinglist.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.repo.ListOrdering
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.repo.ListSettingsRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.ListSections
import dev.otherworld.shoppinglist.domain.sort.SectionKey
import dev.otherworld.shoppinglist.domain.sort.sortLists
import dev.otherworld.shoppinglist.domain.sort.splitLists
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
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
    val error: UiText? = null,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: ListRepository,
    private val credentialStore: CredentialStore,
    private val connectivity: ConnectivityObserver,
    private val listSettings: ListSettingsRepository,
    private val ordering: ListOrdering,
    realtime: RealtimeController,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<UiText?>(null)

    val accountLabel: String = credentialStore.current()?.let {
        "${it.loginName} · ${it.server.removePrefix("https://").removePrefix("http://")}"
    } ?: ""

    val state: StateFlow<ListsUiState> =
        combine(
            repository.observeLists(), listSettings.listSort, listSettings.listOrderSupported, _loading, _error,
        ) { lists, sort, supported, loading, error ->
            ListsUiState(
                loading = loading,
                lists = lists,
                // Without server support (or before it's known) lists keep the server's order.
                sections = if (supported) sortLists(lists, sort, Locale.getDefault()) else splitLists(lists),
                canReorder = supported,
                error = error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState(loading = true))

    init {
        refresh()
        // Refresh instantly when the server pushes a change.
        realtime.events.onEach { poll() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repository.refresh()
                runCatching { listSettings.refresh() }
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
            quietly { repository.refresh() }
            quietly { listSettings.refreshSort() }
        }
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
