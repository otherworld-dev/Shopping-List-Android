package dev.otherworld.shoppinglist.ui.items

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a paste put into the add box, so a pasted list never picks up a search typed there first. */
class PastedTextTest {

    @Test
    fun `a paste into an empty box is all of it`() {
        assertEquals("eggs\nbread", pastedText("", "eggs\nbread"))
    }

    @Test
    fun `a paste after typed text is only what was pasted`() {
        assertEquals("eggs\nbread", pastedText("mil", "mileggs\nbread"))
    }

    @Test
    fun `a paste before typed text is only what was pasted`() {
        assertEquals("eggs\nbread", pastedText("mil", "eggs\nbreadmil"))
    }

    @Test
    fun `a paste in the middle of typed text is only what was pasted`() {
        assertEquals("\nx\n", pastedText("milk", "mi\nx\nlk"))
    }

    @Test
    fun `a paste over selected text is all of it`() {
        assertEquals("eggs\nbread", pastedText("mil", "eggs\nbread"))
    }
}
