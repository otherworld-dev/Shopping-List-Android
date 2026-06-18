package dev.otherworld.shoppinglist.ui.areas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.repo.AreaRepository
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageAreasViewModel @Inject constructor(
    private val areaRepository: AreaRepository,
    listRepository: ListRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L
    val listTitle: String = savedStateHandle.get<String>("title").orEmpty()

    val areas: StateFlow<List<ShopAreaModel>> = areaRepository.observeAreas(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Other lists, available as sources to copy areas from. */
    val otherLists: StateFlow<List<ShoppingListModel>> = listRepository.observeLists()
        .map { lists -> lists.filter { it.id != listId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun createArea(name: String, color: String?, keywords: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { areaRepository.createArea(listId, name.trim(), color, keywords) }
                .onFailure { reportError(it) }
        }
    }

    fun updateArea(id: Long, name: String, color: String?, keywords: List<String>) {
        viewModelScope.launch {
            runCatching { areaRepository.updateArea(listId, id, name.trim(), color, keywords) }
                .onFailure { reportError(it) }
        }
    }

    fun deleteArea(id: Long) {
        viewModelScope.launch {
            runCatching { areaRepository.deleteArea(listId, id) }.onFailure { reportError(it) }
        }
    }

    fun copyFrom(sourceListId: Long) {
        viewModelScope.launch {
            runCatching { areaRepository.copyAreas(listId, sourceListId) }.onFailure { reportError(it) }
        }
    }

    fun consumeError() = _error.update { null }

    private fun reportError(t: Throwable) {
        _error.value = t.message ?: "Something went wrong"
    }
}
