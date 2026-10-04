package dev.otherworld.shoppinglist.data.guest

import org.junit.Assert.assertEquals
import org.junit.Test

// The web app keeps one name per browser (utils/guestName.ts); the app keeps one per phone.
class GuestNameTest {

    @Test
    fun `a name is kept trimmed`() {
        assertEquals("Anna", cleanGuestName("  Anna \n"))
    }

    @Test
    fun `a blank name is no name`() {
        assertEquals("", cleanGuestName("   "))
    }

    // The same limit as the web app's field; the server tidies it again either way.
    @Test
    fun `a name is cut to 40 characters`() {
        assertEquals("A".repeat(40), cleanGuestName("A".repeat(55)))
        // Cutting can leave trailing spaces, which are trimmed again.
        assertEquals("ab", cleanGuestName("ab" + " ".repeat(38) + "cd"))
    }
}
