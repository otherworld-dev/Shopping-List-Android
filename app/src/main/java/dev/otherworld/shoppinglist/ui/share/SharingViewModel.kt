package dev.otherworld.shoppinglist.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.repo.ShareRepository
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShareModel
import dev.otherworld.shoppinglist.domain.model.ShareType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SharingUiState(
    val loading: Boolean = false,
    val people: List<ShareModel> = emptyList(),
    val link: ShareModel? = null,
    val error: String? = null,
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

    init {
        refresh()
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
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load shares") }
            }
        }
    }

    fun addShare(sharedWith: String, type: Int, write: Boolean) {
        if (sharedWith.isBlank()) return
        val permission = if (write) Permission.WRITE else Permission.READ
        mutate { repository.createShare(listId, sharedWith, type, permission) }
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
                _state.update { it.copy(error = e.message ?: "Action failed") }
            }
        }
    }

    companion object {
        const val TYPE_USER = ShareType.USER
        const val TYPE_GROUP = ShareType.GROUP
    }
}
