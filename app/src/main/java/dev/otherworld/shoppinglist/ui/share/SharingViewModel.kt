package dev.otherworld.shoppinglist.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.repo.ShareRepository
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShareModel
import dev.otherworld.shoppinglist.domain.share.ShareeOption
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharingUiState(
    val loading: Boolean = false,
    val people: List<ShareModel> = emptyList(),
    val link: ShareModel? = null,
    val error: UiText? = null,
    val query: String = "",
    val results: List<ShareeOption> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
)

@HiltViewModel
class SharingViewModel @Inject constructor(
    private val repository: ShareRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L
    val listTitle: String = savedStateHandle.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(SharingUiState(loading = true))
    val state: StateFlow<SharingUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        refresh()
        observeSearch()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shares = repository.getShares(listId)
                _state.update {
                    it.copy(
                        loading = false,
                        people = shares.filterNot { s -> s.isLink },
                        link = shares.firstOrNull { s -> s.isLink },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = errorText(e)) }
            }
        }
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text, searching = text.isNotBlank(), searchFailed = false) }
        query.value = text
    }

    /** Shares with edit rights straight away, like the web app; the row's switch changes it. */
    fun shareWith(option: ShareeOption) {
        onQueryChange("")
        mutate { repository.createShare(listId, option.shareWith, option.type, Permission.WRITE) }
    }

    fun setSharePermission(share: ShareModel, write: Boolean) {
        val permission = if (write) Permission.WRITE else Permission.READ
        mutate { repository.updateSharePermission(share.id, permission) }
    }

    fun removeShare(share: ShareModel) = mutate { repository.removeShare(share.id) }

    fun createLink(write: Boolean) {
        val permission = if (write) Permission.WRITE else Permission.READ
        mutate { repository.createLink(listId, permission, null) }
    }

    fun setLinkPermission(share: ShareModel, write: Boolean) {
        val permission = if (write) Permission.WRITE else Permission.READ
        mutate { repository.updateLinkPermission(share.id, permission) }
    }

    fun setLinkPassword(share: ShareModel, password: String?) =
        mutate { repository.setLinkPassword(share.id, password) }

    fun removeLink(share: ShareModel) = mutate { repository.removeLink(share.id) }

    fun linkUrl(token: String): String = repository.linkUrl(token)

    fun consumeError() = _state.update { it.copy(error = null) }

    /** Searches once typing pauses, like the web app's 300 ms debounce; a newer query cancels an older one. */
    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            query.debounce(SEARCH_DELAY_MS).map { it.trim() }.collectLatest { q ->
                if (q.isEmpty()) {
                    _state.update { it.copy(results = emptyList(), searching = false, searchFailed = false) }
                    return@collectLatest
                }
                val found = runCatching { repository.searchSharees(q) }
                _state.update {
                    it.copy(
                        results = found.getOrDefault(emptyList()),
                        searching = false,
                        searchFailed = found.isFailure,
                    )
                }
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                val shares = repository.getShares(listId)
                _state.update {
                    it.copy(
                        people = shares.filterNot { s -> s.isLink },
                        link = shares.firstOrNull { s -> s.isLink },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorText(e)) }
            }
        }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}
