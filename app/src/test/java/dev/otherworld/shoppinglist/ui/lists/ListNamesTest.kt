package dev.otherworld.shoppinglist.ui.lists

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ListNamesTest {

    @Test
    fun `a blank list is named after the day in the phone's date style`() {
        val day = LocalDate.of(2026, 11, 11)
        assertEquals("11 Nov 2026", defaultListName(day, Locale.UK))
        assertEquals("Nov 11, 2026", defaultListName(day, Locale.US))
    }
}
