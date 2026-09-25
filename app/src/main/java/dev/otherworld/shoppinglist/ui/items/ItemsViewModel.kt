package dev.otherworld.shoppinglist.ui.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.prefs.Density
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.repo.AreaRepository
import dev.otherworld.shoppinglist.data.repo.ItemRepository
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
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
    /** Writable other lists — targets for "Move to list". */
    val otherLists: List<ShoppingListModel> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
    val density: Density = Density.COMFY,
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>("listId") ?: 0L
    private val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val canWrite: Boolean = savedStateHandle.get<Boolean>("canWrite") ?: true

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _notice = MutableStateFlow<String?>(null)

    val state: StateFlow<ItemsUiState> = combine(
        repository.observeItems(listId),
        repository.observeAreas(listId),
        listRepository.observeLists(),
        _loading,
        _error,
        _notice,
        displayPrefs.density,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ItemsUiState(
            listId = listId,
            title = title,
            canWrite = canWrite,
            loading = values[3] as Boolean,
            items = values[0] as List<ItemModel>,
            areas = values[1] as List<ShopAreaModel>,
            // Writable other lists as move targets, sorted by title (matches the web app).
            otherLists = (values[2] as List<ShoppingListModel>)
                .filter { it.id != listId && it.id > 0 && (it.isOwner || it.canWrite) }
                .sortedBy { it.title.lowercase() },
            error = values[4] as String?,
            notice = values[5] as String?,
            density = values[6] as Density,
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
            _error.value = "This item hasn't synced yet — try again in a moment."
            return
        }
        if (!connectivity.isOnline.value) {
            _error.value = "You're offline — moving items needs a connection."
            return
        }
        viewModelScope.launch {
            runCatching { repository.moveItem(item, target.id) }
                .onSuccess { _notice.value = "Moved \"${item.name}\" to ${target.title}" }
                .onFailure { _error.value = "Couldn't move the item." }
        }
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

    fun consumeNotice() = _notice.update { null }
}
