package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.domain.sort.ListSections
import dev.otherworld.shoppinglist.domain.sort.ListSortMode
import dev.otherworld.shoppinglist.domain.sort.SectionKey
import dev.otherworld.shoppinglist.domain.sort.freezeUnplaced
import dev.otherworld.shoppinglist.domain.sort.planListReorder
import dev.otherworld.shoppinglist.domain.sort.sortLists
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves a new list order, keeping what the user sees when it switches the sort to Custom
 * (see planListReorder). Shared by the lists screen and the Settings screen.
 */
@Singleton
class ListOrdering @Inject constructor(
    private val lists: ListRepository,
    private val settings: ListSettingsRepository,
) {
    suspend fun reorderSection(key: SectionKey, order: List<Long>) {
        val plan = planListReorder(currentSections(), key, order, settings.listSort.value)
        plan.saves.forEach { lists.reorderLists(it) }
        if (plan.switchToCustom) settings.setListSort(ListSortMode.CUSTOM)
    }

    suspend fun setListSort(mode: ListSortMode) {
        if (mode == settings.listSort.value) return
        // Choosing Custom from Settings must keep a saved custom order: only sections where no
        // list has ever been placed are saved as shown, matching the web app's freezeUnplaced.
        if (mode == ListSortMode.CUSTOM) freezeUnplaced(currentSections()).forEach { lists.reorderLists(it) }
        settings.setListSort(mode)
    }

    private suspend fun currentSections(): ListSections =
        sortLists(lists.observeLists().first(), settings.listSort.value, Locale.getDefault())
}
