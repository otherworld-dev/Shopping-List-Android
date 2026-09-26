package dev.otherworld.shoppinglist.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `follows the phone when nothing was saved`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
    }

    @Test
    fun `reads back each saved choice`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStorage(it.storageValue)) }
    }

    @Test
    fun `treats an unknown value as following the phone`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("sepia"))
    }

    @Test
    fun `system follows the phone and the others ignore it`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
