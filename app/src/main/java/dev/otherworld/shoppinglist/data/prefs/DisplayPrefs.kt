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

/** How big the app's text is, on top of the phone's own font size (issue #16). */
enum class TextScale(val storageValue: String, val factor: Float) {
    SMALL("small", 0.9f),
    DEFAULT("default", 1f),
    LARGE("large", 1.15f),
    LARGER("larger", 1.3f);

    /**
     * The font scale to use on a phone set to [system]. Kept to 2x at most, where Android's
     * gentle (non-linear) scaling ends and big text would suddenly grow in a straight line;
     * a phone already past that is left as it is.
     */
    fun fontScaleOn(system: Float): Float = (system * factor).coerceAtMost(maxOf(MAX_FONT_SCALE, system))

    companion object {
        private const val MAX_FONT_SCALE = 2f

        fun fromStorage(raw: String?): TextScale = entries.firstOrNull { it.storageValue == raw } ?: DEFAULT
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
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _density = MutableStateFlow(read())
    val density: StateFlow<Density> = _density.asStateFlow()

    private val _openSort = MutableStateFlow(OpenSort.fromStorage(prefs.getString(KEY_OPEN_SORT, null)))
    val openSort: StateFlow<OpenSort> = _openSort.asStateFlow()

    private val _boughtSort = MutableStateFlow(BoughtSort.fromStorage(prefs.getString(KEY_BOUGHT_SORT, null)))
    val boughtSort: StateFlow<BoughtSort> = _boughtSort.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromStorage(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _textScale = MutableStateFlow(TextScale.fromStorage(prefs.getString(KEY_TEXT_SCALE, null)))
    val textScale: StateFlow<TextScale> = _textScale.asStateFlow()

    // Off until turned on, like the web app's switch; on the phone it's this device's own choice.
    private val _showImages = MutableStateFlow(prefs.getBoolean(KEY_SHOW_IMAGES, false))
    val showImages: StateFlow<Boolean> = _showImages.asStateFlow()

    // On until turned off: the headings are what fold an area and take an item dragged to another (#24).
    private val _showAreaHeadings = MutableStateFlow(prefs.getBoolean(KEY_AREA_HEADINGS, true))
    val showAreaHeadings: StateFlow<Boolean> = _showAreaHeadings.asStateFlow()

    // The last list until something else is chosen: most people go back to the same list.
    private val _startList = MutableStateFlow(
        StartList.fromStorage(
            openLastList = prefs.getBoolean(KEY_OPEN_LAST_LIST, true),
            chosenListId = if (prefs.contains(KEY_START_LIST)) prefs.getLong(KEY_START_LIST, 0L) else null,
        ),
    )
    val startList: StateFlow<StartList> = _startList.asStateFlow()

    /** The list opened last on this phone (any kind: own, shared, link or phone-only), or null. */
    val lastListId: Long?
        get() = if (prefs.contains(KEY_LAST_LIST)) prefs.getLong(KEY_LAST_LIST, 0L) else null

    private val collapsedFlows = mutableMapOf<Long, MutableStateFlow<Set<String>>>()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().apply {
            if (mode == ThemeMode.SYSTEM) remove(KEY_THEME) else putString(KEY_THEME, mode.storageValue)
        }.apply()
    }

    fun setTextScale(scale: TextScale) {
        _textScale.value = scale
        prefs.edit().apply {
            if (scale == TextScale.DEFAULT) remove(KEY_TEXT_SCALE) else putString(KEY_TEXT_SCALE, scale.storageValue)
        }.apply()
    }

    fun setShowImages(show: Boolean) {
        _showImages.value = show
        prefs.edit().putBoolean(KEY_SHOW_IMAGES, show).apply()
    }

    fun setShowAreaHeadings(show: Boolean) {
        _showAreaHeadings.value = show
        prefs.edit().apply {
            if (show) remove(KEY_AREA_HEADINGS) else putBoolean(KEY_AREA_HEADINGS, false)
        }.apply()
    }

    @Synchronized
    fun setStartList(start: StartList) {
        _startList.value = start
        // The last list, the default, removes both entries.
        prefs.edit().apply {
            when (start) {
                StartList.LastUsed -> remove(KEY_OPEN_LAST_LIST).remove(KEY_START_LIST)
                StartList.AllLists -> putBoolean(KEY_OPEN_LAST_LIST, false).remove(KEY_START_LIST)
                is StartList.Chosen -> remove(KEY_OPEN_LAST_LIST).putLong(KEY_START_LIST, start.listId)
            }
        }.apply()
    }

    fun setLastListId(id: Long) {
        prefs.edit().putLong(KEY_LAST_LIST, id).apply()
    }

    /** Follows a list made offline or on the phone to its server id, so it's still the one opened. */
    @Synchronized
    fun remapList(oldId: Long, newId: Long) {
        if (lastListId == oldId) setLastListId(newId)
        val next = _startList.value.remapped(oldId, newId)
        if (next != _startList.value) setStartList(next)
    }

    /** Forgets [listId] once it's gone from the phone. */
    @Synchronized
    fun forgetList(listId: Long) {
        if (lastListId == listId) prefs.edit().remove(KEY_LAST_LIST).apply()
        val next = _startList.value.forgetting(listId)
        if (next != _startList.value) setStartList(next)
    }

    /** On logout: the lists opened or chosen here may have been that account's. */
    @Synchronized
    fun forgetAccountLists() {
        prefs.edit().remove(KEY_LAST_LIST).apply()
        if (_startList.value is StartList.Chosen) setStartList(StartList.AllLists)
    }

    fun setDensity(density: Density) {
        _density.value = density
        prefs.edit().putBoolean(KEY_COMPACT, density == Density.COMPACT).apply()
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

    companion object {
        private const val PREFS_NAME = "display_prefs"

        /**
         * The saved text size, read straight from storage: the activity needs it in
         * attachBaseContext, before anything can be injected.
         */
        fun storedTextScale(context: Context): TextScale = TextScale.fromStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TEXT_SCALE, null),
        )

        private const val KEY_COMPACT = "compact_rows"
        private const val KEY_OPEN_SORT = "open_sort"
        private const val KEY_BOUGHT_SORT = "bought_sort"
        private const val KEY_THEME = "theme"
        private const val KEY_SHOW_IMAGES = "show_images"
        private const val KEY_OPEN_LAST_LIST = "open_last_list"
        private const val KEY_LAST_LIST = "last_list_id"
        private const val KEY_START_LIST = "start_list_id"
        private const val KEY_AREA_HEADINGS = "area_headings"
        private const val KEY_TEXT_SCALE = "text_scale"
    }
}
