package dev.otherworld.shoppinglist.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How tightly the item list is packed. */
enum class Density { COMFY, COMPACT }

/**
 * Local, per-device display preferences that don't sync to the server. Backed by
 * SharedPreferences and exposed as a StateFlow so the list re-renders when it changes,
 * mirroring [dev.otherworld.shoppinglist.data.theme.ServerTheme].
 */
@Singleton
class DisplayPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("display_prefs", Context.MODE_PRIVATE)

    private val _density = MutableStateFlow(read())
    val density: StateFlow<Density> = _density.asStateFlow()

    fun toggleDensity() {
        val next = if (_density.value == Density.COMPACT) Density.COMFY else Density.COMPACT
        _density.value = next
        prefs.edit().putBoolean(KEY_COMPACT, next == Density.COMPACT).apply()
    }

    // Defaults to COMFY, so existing installs are unchanged until the user opts in.
    private fun read(): Density =
        if (prefs.getBoolean(KEY_COMPACT, false)) Density.COMPACT else Density.COMFY

    private companion object {
        const val KEY_COMPACT = "compact_rows"
    }
}
