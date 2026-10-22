package dev.otherworld.shoppinglist.data.prefs

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayPrefsTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        // The prefs file persists on the device between runs; start each test from a clean slate.
        context.getSharedPreferences("display_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultsToComfy() {
        assertEquals(Density.COMFY, DisplayPrefs(context).density.value)
    }

    @Test
    fun setDensityEmits() {
        val prefs = DisplayPrefs(context)
        prefs.setDensity(Density.COMPACT)
        assertEquals(Density.COMPACT, prefs.density.value)
        prefs.setDensity(Density.COMFY)
        assertEquals(Density.COMFY, prefs.density.value)
    }

    @Test
    fun choiceSurvivesRestart() {
        DisplayPrefs(context).setDensity(Density.COMPACT) // persisted
        // A fresh instance models the next app launch reading from disk.
        assertEquals(Density.COMPACT, DisplayPrefs(context).density.value)
    }

    @Test
    fun foldedSectionsSurviveRestart() {
        DisplayPrefs(context).toggleCollapsed(1L, "checked")
        assertEquals(setOf("checked"), DisplayPrefs(context).collapsedAreas(1L).value)
    }
}
