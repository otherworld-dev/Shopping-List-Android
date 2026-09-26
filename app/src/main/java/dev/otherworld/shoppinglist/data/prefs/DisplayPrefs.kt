package dev.otherworld.shoppinglist.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.CollapsedAreas
import dev.otherworld.shoppinglist.domain.sort.OpenSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How tightly the item list is packed. */
enum class Density { COMFY, COMPACT }

/** Light or dark, or whatever the phone is set to. */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): ThemeMode = entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

/**
 * Local, per-device display preferences that don't sync to the server. Backed by
 * SharedPreferences and exposed as StateFlows so the list re-renders when they change,
 * mirroring [dev.otherworld.shoppinglist.data.theme.ServerTheme].
 *
 * The sort choices and collapsed areas are the web app's view preferences (localStorage
 * there): one sort choice for all lists, collapsed areas remembered per list, and none of it
 * shared with the others on a list.
 */
@Singleton
class DisplayPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("display_prefs", Context.MODE_PRIVATE)

    private val _density = MutableStateFlow(read())
    val density: StateFlow<Density> = _density.asStateFlow()

    private val _openSort = MutableStateFlow(OpenSort.fromStorage(prefs.getString(KEY_OPEN_SORT, null)))
    val openSort: StateFlow<OpenSort> = _openSort.asStateFlow()

    private val _boughtSort = MutableStateFlow(BoughtSort.fromStorage(prefs.getString(KEY_BOUGHT_SORT, null)))
    val boughtSort: StateFlow<BoughtSort> = _boughtSort.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromStorage(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val collapsedFlows = mutableMapOf<Long, MutableStateFlow<Set<String>>>()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().apply {
            if (mode == ThemeMode.SYSTEM) remove(KEY_THEME) else putString(KEY_THEME, mode.storageValue)
        }.apply()
    }

    fun toggleDensity() {
        val next = if (_density.value == Density.COMPACT) Density.COMFY else Density.COMPACT
        _density.value = next
        prefs.edit().putBoolean(KEY_COMPACT, next == Density.COMPACT).apply()
    }

    fun setOpenSort(sort: OpenSort) {
        _openSort.value = sort
        // Choosing the default removes the entry, like the web app's saved choices.
        prefs.edit().apply {
            if (sort == OpenSort.AREA) remove(KEY_OPEN_SORT) else putString(KEY_OPEN_SORT, sort.storageValue)
        }.apply()
    }

    fun setBoughtSort(sort: BoughtSort) {
        _boughtSort.value = sort
        prefs.edit().apply {
            if (sort == BoughtSort.AREA) remove(KEY_BOUGHT_SORT) else putString(KEY_BOUGHT_SORT, sort.storageValue)
        }.apply()
    }

    /** The area groups the viewer has folded away on [listId], as [CollapsedAreas] keys. */
    fun collapsedAreas(listId: Long): StateFlow<Set<String>> = collapsedFlow(listId).asStateFlow()

    fun toggleCollapsed(listId: Long, areaId: Long?) {
        val flow = collapsedFlow(listId)
        val next = CollapsedAreas.toggled(flow.value, areaId)
        flow.value = next
        prefs.edit().apply {
            // An empty set removes the entry, so lists that are fully open leave nothing behind.
            if (next.isEmpty()) remove(collapsedKey(listId)) else putStringSet(collapsedKey(listId), next)
        }.apply()
    }

    @Synchronized
    private fun collapsedFlow(listId: Long): MutableStateFlow<Set<String>> =
        collapsedFlows.getOrPut(listId) {
            MutableStateFlow(prefs.getStringSet(collapsedKey(listId), emptySet()).orEmpty().toSet())
        }

    // Defaults to COMFY, so existing installs are unchanged until the user opts in.
    private fun read(): Density =
        if (prefs.getBoolean(KEY_COMPACT, false)) Density.COMPACT else Density.COMFY

    private fun collapsedKey(listId: Long) = "collapsed_areas.$listId"

    private companion object {
        const val KEY_COMPACT = "compact_rows"
        const val KEY_OPEN_SORT = "open_sort"
        const val KEY_BOUGHT_SORT = "bought_sort"
        const val KEY_THEME = "theme"
    }
}
