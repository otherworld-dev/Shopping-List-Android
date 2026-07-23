package dev.otherworld.shoppinglist.ui.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.prefs.Density
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.repo.AreaRepository
import dev.otherworld.shoppinglist.data.repo.ItemRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.text.SmartInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemsUiState(
    val listId: Long = 0,
    val title: String = "",
    val canWrite: Boolean = true,
    val loading: Boolean = false,
    val items: List<ItemModel> = emptyList(),
    val areas: List<ShopAreaModel> = emptyList(),
    val error: String? = null,
    val density: Density = Density.COMFY,
)

@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val areaRepository: AreaRepository,
    private val smartInput: SmartInput,
    private val connectivity: ConnectivityObserver,
    private val realtime: RealtimeController,
    private val syncEngine: SyncEngine,
    private val displayPrefs: DisplayPrefs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L
    private val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val canWrite: Boolean = savedStateHandle.get<Boolean>("canWrite") ?: true

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<ItemsUiState> = combine(
        repository.observeItems(listId),
        repository.observeAreas(listId),
        _loading,
        _error,
        displayPrefs.density,
    ) { items, areas, loading, error, density ->
        ItemsUiState(listId, title, canWrite, loading, items, areas, error, density)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ItemsUiState(
            listId = listId,
            title = title,
            canWrite = canWrite,
            loading = true,
            density = displayPrefs.density.value,
        ),
    )

    init {
        refresh()
        realtime.ensureConnected()
        viewModelScope.launch { realtime.events.collect { poll() } }
        // Surface durable background-sync failures (a queued mutation was given up on).
        viewModelScope.launch {
            syncEngine.failures.collect { _error.value = "Some changes couldn't be saved to the server." }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repository.refresh(listId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load"
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
     */
    fun addItem(rawInput: String, explicitAreaId: Long?) {
        val current = state.value
        val plan = smartInput.planAdd(rawInput, current.areas, current.items, explicitAreaId) ?: return
        viewModelScope.launch {
            when (plan) {
                is SmartInput.AddPlan.Create -> repository.createItem(
                    listId = listId,
                    name = plan.name,
                    quantity = plan.quantity,
                    shopAreaId = plan.shopAreaId,
                    areaExplicit = plan.areaExplicit,
                )
                is SmartInput.AddPlan.Merge -> repository.updateItem(
                    plan.target,
                    name = plan.newName,
                    quantity = plan.quantity,
                )
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
                .onFailure { _error.value = it.message ?: "Couldn't save the area order" }
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

    fun consumeError() = _error.update { null }
}
