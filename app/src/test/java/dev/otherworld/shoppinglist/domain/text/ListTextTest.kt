package dev.otherworld.shoppinglist.domain.text

import dev.otherworld.shoppinglist.domain.model.ItemModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of the web app's `utils/listText.test.ts` (1.7.0). */
class ListTextTest {

    private var nextId = 1L

    private fun item(
        name: String,
        quantity: String? = null,
        unit: String? = null,
        checked: Boolean = false,
    ) = ItemModel(
        id = nextId++, listId = 1, name = name, quantity = quantity, unit = unit,
        shopAreaId = null, checked = checked, checkedBy = null, sortOrder = 0,
    )

    @Test
    fun `writes one item per line`() {
        assertEquals("Milk\nBananas", formatListAsText(listOf(item("Milk"), item("Bananas"))))
    }

    @Test
    fun `puts the quantity before the name`() {
        assertEquals("6 Eggs", formatListAsText(listOf(item("Eggs", quantity = "6"))))
    }

    @Test
    fun `puts the unit between the quantity and the name`() {
        assertEquals("2 cups Flour", formatListAsText(listOf(item("Flour", quantity = "2", unit = "cups"))))
    }

    @Test
    fun `omits a quantity of 1 which is the implicit default`() {
        assertEquals("Milk", formatListAsText(listOf(item("Milk", quantity = "1"))))
    }

    @Test
    fun `keeps a quantity of 1 when it carries a unit`() {
        assertEquals("1 l Milk", formatListAsText(listOf(item("Milk", quantity = "1", unit = "l"))))
    }

    @Test
    fun `skips checked items`() {
        val text = formatListAsText(listOf(item("Milk"), item("Bread", checked = true), item("Bananas")))
        assertEquals("Milk\nBananas", text)
    }

    @Test
    fun `returns an empty string when there is nothing to write`() {
        assertEquals("", formatListAsText(emptyList()))
        assertEquals("", formatListAsText(listOf(item("Bread", checked = true))))
    }

    @Test
    fun `round trips through the paste parser`() {
        val smart = SmartInput.english()
        val items = listOf(
            item("Flour", quantity = "2", unit = "cups"),
            item("Milk"),
            item("Eggs", quantity = "6"),
        )
        val parsed = SmartInput.splitLines(formatListAsText(items)).map { smart.parseIngredient(it) }
        assertEquals(
            listOf(
                SmartInput.ParsedIngredient("Flour", "2 cups"),
                SmartInput.ParsedIngredient("Milk", null),
                SmartInput.ParsedIngredient("Eggs", "6"),
            ),
            parsed,
        )
    }
}
