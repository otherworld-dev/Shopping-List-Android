package dev.otherworld.shoppinglist.domain.guest

import org.junit.Assert.assertEquals
import org.junit.Test

// Ported from the web app's src/utils/attribution.test.ts.
class AttributionTest {

    private val base = Attributed(
        addedBy = null, addedByName = null, addedByGuest = false,
        checkedBy = null, checkedByName = null, checkedByGuest = false,
    )

    @Test
    fun `names an account user plainly and marks a guest separately`() {
        assertEquals(Byline("Ben", guest = false), attribution(base.copy(addedBy = "ben", addedByName = "Ben"), "adam", false).added)
        assertEquals(Byline("Anna", guest = true), attribution(base.copy(addedByName = "Anna", addedByGuest = true), "adam", false).added)
    }

    @Test
    fun `keeps the guest mark apart from the name, so cutting a long name never hides it`() {
        val long = "Adam Morgan, owner of this list"
        assertEquals(Byline(long, guest = true), attribution(base.copy(addedByName = long, addedByGuest = true), "adam", false).added)
    }

    @Test
    fun `shows nothing for an item from before names were kept`() {
        assertEquals(Attribution(added = null, checked = null), attribution(base, "adam", false))
    }

    @Test
    fun `hides only your own name unless you want it`() {
        val item = base.copy(addedBy = "adam", addedByName = "Adam", checkedByName = "Anna", checkedByGuest = true)
        assertEquals(Attribution(added = null, checked = Byline("Anna", guest = true)), attribution(item, "adam", false))
        assertEquals(
            Attribution(added = Byline("Adam", guest = false), checked = Byline("Anna", guest = true)),
            attribution(item, "adam", true),
        )
    }

    @Test
    fun `shows members as members on a guest list, where the server leaves out user ids`() {
        val item = base.copy(addedByName = "Adam", checkedByName = "Ben")
        assertEquals(
            Attribution(added = Byline("Adam", guest = false), checked = Byline("Ben", guest = false)),
            attribution(item, null, false),
        )
    }

    @Test
    fun `treats a guest who happens to share your name as a guest`() {
        assertEquals(Byline("Adam", guest = true), attribution(base.copy(addedByName = "Adam", addedByGuest = true), "adam", false).added)
    }

    @Test
    fun `the row names who ticked a ticked item, otherwise who added it`() {
        val item = base.copy(addedByName = "Adam", checkedByName = "Anna", checkedByGuest = true)
        assertEquals(Byline("Anna", guest = true), bylineFor(item, checked = true, me = null, showOwn = false))
        assertEquals(Byline("Adam", guest = false), bylineFor(item, checked = false, me = null, showOwn = false))
    }
}
