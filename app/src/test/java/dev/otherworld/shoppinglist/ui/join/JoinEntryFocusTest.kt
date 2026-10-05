package dev.otherworld.shoppinglist.ui.join

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Server field only takes the focus after a paste: typing a host whose first part happens to
// look like a code ("nextcamp.example.com") mustn't send the rest of it into the Server field.
class JoinEntryFocusTest {

    @Test
    fun `several characters at once is a paste`() {
        assertTrue(looksPasted("", "K7QM-3XPD"))
        assertTrue(looksPasted("K7", "K7QM-3XPD"))
    }

    @Test
    fun `one character at a time is typing`() {
        assertFalse(looksPasted("nextcam", "nextcamp"))
        assertFalse(looksPasted("K7QM-3XPD", "K7QM-3XP"))
        assertFalse(looksPasted("", ""))
    }
}
