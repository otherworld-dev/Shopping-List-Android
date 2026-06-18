package dev.otherworld.shoppinglist.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListsUiState(
    val loading: Boolean = false,
    val lists: List<ShoppingListModel> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: ListRepository,
    private val credentialStore: CredentialStore,
    private val connectivity: ConnectivityObserver,
    realtime: RealtimeController,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val accountLabel: String = credentialStore.current()?.let {
        "${it.loginName} · ${it.server.removePrefix("https://").removePrefix("http://")}"
    } ?: ""

    val state: StateFlow<ListsUiState> =
        combine(repository.observeLists(), _loading, _error) { lists, loading, error ->
            ListsUiState(loading = loading, lists = lists, error = error)
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
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load lists"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Silent background refresh used by the polling loop; no-ops while offline. */
    fun poll() {
        if (!connectivity.isOnline.value) return
        viewModelScope.launch { runCatching { repository.refresh() } }
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

    fun logout() = credentialStore.clear()

    fun consumeError() = _error.update { null }
}
