package dev.otherworld.shoppinglist.data.photo

import dev.otherworld.shoppinglist.data.local.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web app's imageSpread tests, as the changed rows to write back. */
class PhotoSpreadTest {

    private fun item(id: Long, name: String, imageKey: String?) = ItemEntity(
        id = id, listId = 1, name = name, quantity = null, unit = null, shopAreaId = null,
        checked = false, checkedBy = null, sortOrder = 0, updatedAt = null, imageKey = imageKey,
    )

    private val a = "aaaaaaaaaaaaaaaa"
    private val b = "bbbbbbbbbbbbbbbb"
    private val c = "cccccccccccccccc"

    private fun rows() = listOf(
        item(1, "Cheese", null),
        item(2, "  CHEESE ", a),
        item(3, "Milk", a),
        item(4, "Cheddar cheese", null),
    )

    @Test
    fun `the name key is trimmed and ignores case`() {
        assertEquals("cheese", imageNameKey("  CHEESE "))
    }

    @Test
    fun `gives the key to every item with the same name, ignoring case and spaces`() {
        val changed = spreadImageKey(rows(), "cheese", b)
        assertEquals(listOf(1L, 2L), changed.map { it.id })
        assertTrue(changed.all { it.imageKey == b })
    }

    @Test
    fun `leaves items that already show the key alone`() {
        assertEquals(listOf(1L), spreadImageKey(rows(), "cheese", a).map { it.id })
    }

    @Test
    fun `does nothing for a blank name`() {
        assertTrue(spreadImageKey(listOf(item(1, " ", null)), "  ", b).isEmpty())
    }

    @Test
    fun `takes the photo off every item showing that key`() {
        val changed = clearImageKeys(rows(), a, "Cheese")
        assertEquals(listOf(2L, 3L), changed.map { it.id })
        assertTrue(changed.all { it.imageKey == null })
    }

    @Test
    fun `also clears same-named items that show another photo`() {
        val items = listOf(item(1, "Cheese", a), item(2, "cheese", c), item(3, "Milk", c))
        assertEquals(listOf(1L, 2L), clearImageKeys(items, a, "Cheese").map { it.id })
    }
}
