package dev.otherworld.shoppinglist.domain.text

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInputTest {

    private val smart = SmartInput.english()

    private fun item(
        id: Long,
        name: String,
        quantity: String? = null,
        checked: Boolean = false,
    ) = ItemModel(
        id = id, listId = 1, name = name, quantity = quantity, unit = null,
        shopAreaId = null, checked = checked, checkedBy = null, sortOrder = 0,
    )

    private fun area(id: Long, name: String, keywords: List<String>, sortOrder: Int = 0) =
        ShopAreaModel(id = id, listId = 1, name = name, sortOrder = sortOrder, color = null, keywords = keywords)

    // ---- Ingredient parsing ----

    @Test
    fun `parses quantity and unit`() {
        val p = smart.parseIngredient("2 cups flour")
        assertEquals("Flour", p.name)
        assertEquals("2 cups", p.quantity)
    }

    @Test
    fun `plain name has no quantity and is capitalized`() {
        val p = smart.parseIngredient("milk")
        assertEquals("Milk", p.name)
        assertNull(p.quantity)
    }

    @Test
    fun `parses simple fraction with unit`() {
        val p = smart.parseIngredient("1/2 cup sugar")
        assertEquals("Sugar", p.name)
        assertEquals("1/2 cup", p.quantity)
    }

    @Test
    fun `parses mixed fraction`() {
        val p = smart.parseIngredient("2 1/2 cups water")
        assertEquals("Water", p.name)
        assertEquals("2 1/2 cups", p.quantity)
    }

    @Test
    fun `leading unit without number`() {
        val p = smart.parseIngredient("pinch of salt")
        assertEquals("Salt", p.name)
        assertEquals("1 pinch", p.quantity)
    }

    @Test
    fun `quantity without recognized unit`() {
        val p = smart.parseIngredient("3 eggs")
        assertEquals("Eggs", p.name)
        assertEquals("3", p.quantity)
    }

    @Test
    fun `strips parentheticals from name`() {
        val p = smart.parseIngredient("2 cups (packed) flour")
        assertEquals("Flour", p.name)
        assertEquals("2 cups", p.quantity)
    }

    // ---- Normalization & morphology ----

    @Test
    fun `normalize singularizes and folds`() {
        assertEquals("apple", smart.normalizeName("Apples"))
        assertEquals("apple", smart.normalizeName("apple"))
        assertEquals("tomato", smart.normalizeName("Tomatoes"))
        assertEquals("cafe", smart.normalizeName("Café"))
    }

    @Test
    fun `normalize strips trailing notes`() {
        assertEquals("chicken breast", smart.normalizeName("Chicken breast, diced"))
    }

    @Test
    fun `singularize irregular plurals`() {
        assertEquals("berry", smart.singularize("berries"))
        assertEquals("knife", smart.singularize("knives"))
        assertEquals("loaf", smart.singularize("loaves"))
        assertEquals("radish", smart.singularize("radishes"))
        assertEquals("box", smart.singularize("boxes"))
        assertEquals("grass", smart.singularize("grass"))  // -ss not stripped
        assertEquals("cheese", smart.singularize("cheese")) // exception
    }

    @Test
    fun `pluralize names`() {
        assertEquals("apples", smart.pluralizeName("apple"))
        assertEquals("berries", smart.pluralizeName("berry"))
        assertEquals("knives", smart.pluralizeName("knife"))
        assertEquals("tomatoes", smart.pluralizeName("tomato"))
        assertEquals("apples", smart.pluralizeName("apples")) // already plural — unchanged
        assertEquals("cheese", smart.pluralizeName("cheese")) // exception — unchanged
    }

    @Test
    fun `pluralize preserves preceding words and casing`() {
        assertEquals("Red apples", smart.pluralizeName("Red apple"))
    }

    // ---- Quantity merge ----

    @Test
    fun `merge same unit adds`() {
        assertEquals("3.5 cups", smart.mergeQuantities("2 cups", "1.5 cups"))
    }

    @Test
    fun `merge bare numbers adds`() {
        assertEquals("3", smart.mergeQuantities("2", "1"))
    }

    @Test
    fun `merge different units concatenates`() {
        assertEquals("2 cups + 500 g", smart.mergeQuantities("2 cups", "500 g"))
    }

    @Test
    fun `merge with empty returns other`() {
        assertEquals("3", smart.mergeQuantities(null, "3"))
        assertEquals("2 cups", smart.mergeQuantities("2 cups", null))
    }

    // ---- Area detection ----

    @Test
    fun `detect area by keyword`() {
        val areas = listOf(
            area(1, "Produce", listOf("apple", "banana"), 0),
            area(2, "Dairy", listOf("milk", "cheese"), 1),
        )
        assertEquals(2L, smart.detectArea("Swiss cheese", areas))
        assertEquals(2L, smart.detectArea("Milk", areas))
        assertEquals(1L, smart.detectArea("Green apple", areas))
        assertNull(smart.detectArea("Batteries", areas))
    }

    @Test
    fun `detect area prefers longest keyword`() {
        val areas = listOf(
            area(1, "Deli", listOf("ham"), 0),
            area(2, "Personal Care", listOf("shampoo"), 1),
        )
        // "shampoo" contains "ham" but the longer keyword wins
        assertEquals(2L, smart.detectArea("Shampoo", areas))
    }

    // ---- Add planning (create vs merge) ----

    @Test
    fun `plan create when no match`() {
        val areas = listOf(area(1, "Produce", listOf("banana"), 0))
        val plan = smart.planAdd("2 bananas", areas, emptyList(), explicitAreaId = null)
        assertTrue(plan is SmartInput.AddPlan.Create)
        plan as SmartInput.AddPlan.Create
        assertEquals("Bananas", plan.name)
        assertEquals("2", plan.quantity)
        assertEquals(1L, plan.shopAreaId)        // auto-detected
        assertEquals(false, plan.areaExplicit)
    }

    @Test
    fun `plan respects explicit area`() {
        val areas = listOf(area(1, "Produce", listOf("banana"), 0))
        val plan = smart.planAdd("bananas", areas, emptyList(), explicitAreaId = 9L)
        plan as SmartInput.AddPlan.Create
        assertEquals(9L, plan.shopAreaId)
        assertTrue(plan.areaExplicit)
    }

    @Test
    fun `plan merges into existing unchecked item`() {
        val existing = listOf(item(5, "Apple", quantity = "2"))
        val plan = smart.planAdd("3 apples", emptyList(), existing, explicitAreaId = null)
        assertTrue(plan is SmartInput.AddPlan.Merge)
        plan as SmartInput.AddPlan.Merge
        assertEquals(5L, plan.target.id)
        assertEquals("5", plan.quantity)
    }

    @Test
    fun `plan pluralizes when crossing from one to many`() {
        val existing = listOf(item(5, "Apple", quantity = "1"))
        val plan = smart.planAdd("apple", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Merge
        assertEquals("2", plan.quantity)
        assertEquals("Apples", plan.newName) // casing of the existing item name is preserved
    }

    @Test
    fun `plan ignores checked items when matching`() {
        val existing = listOf(item(5, "Apple", quantity = "2", checked = true))
        val plan = smart.planAdd("apple", emptyList(), existing, explicitAreaId = null)
        assertTrue(plan is SmartInput.AddPlan.Create)
    }

    // ---- List markup (web app 1.7.0: stripListMarkup) ----

    @Test
    fun `markup rewrites a bracketed quantity so the quantity parser can read it`() {
        assertEquals("10 Aepfel", smart.stripListMarkup("[ 10 ] Aepfel"))
    }

    @Test
    fun `markup removes an empty checkbox`() {
        assertEquals("Milk", smart.stripListMarkup("[ ] Milk"))
    }

    @Test
    fun `markup strips a ticked checkbox from the text`() {
        assertEquals("10, Aepfel", smart.stripListMarkup("[x] 10, Aepfel"))
        assertEquals("Bread", smart.stripListMarkup("[X] Bread"))
    }

    @Test
    fun `markup removes bullet markers`() {
        assertEquals("Bananas", smart.stripListMarkup("- Bananas"))
        assertEquals("Bananas", smart.stripListMarkup("* Bananas"))
        assertEquals("Bananas", smart.stripListMarkup("\u2022 Bananas"))
    }

    @Test
    fun `markup removes an ordered-list index without reading it as a quantity`() {
        assertEquals("Milk", smart.stripListMarkup("1. Milk"))
        assertEquals("Bread", smart.stripListMarkup("2) Bread"))
    }

    @Test
    fun `markup leaves an unmarked line alone`() {
        assertEquals("Plain item", smart.stripListMarkup("Plain item"))
        assertEquals("2 cups flour", smart.stripListMarkup("2 cups flour"))
    }

    @Test
    fun `markup does not mistake a decimal for an ordered-list index`() {
        assertEquals("1.5 kg potatoes", smart.stripListMarkup("1.5 kg potatoes"))
    }

    @Test
    fun `markup keeps a negative-looking token intact`() {
        assertEquals("-3", smart.stripListMarkup("-3"))
    }

    @Test
    fun `parses a bracketed quantity`() {
        assertEquals(SmartInput.ParsedIngredient("Knusperstangen", "1"), smart.parseIngredient("[ 1 ] Knusperstangen"))
        assertEquals(SmartInput.ParsedIngredient("Aepfel", "10"), smart.parseIngredient("[ 10 ] Aepfel"))
    }

    @Test
    fun `parses a checkbox line with a quantity`() {
        assertEquals(SmartInput.ParsedIngredient("Aepfel", "10", checked = true), smart.parseIngredient("[x] 10, Aepfel"))
    }

    @Test
    fun `parses a bulleted line`() {
        assertEquals(SmartInput.ParsedIngredient("Bananas", null), smart.parseIngredient("- Bananas"))
    }

    // ---- Capitalisation ----

    @Test
    fun `leaves an intercapped product name alone`() {
        assertEquals("iPhone charger", smart.parseIngredient("iPhone charger").name)
        assertEquals("eBay voucher", smart.parseIngredient("eBay voucher").name)
    }

    @Test
    fun `still capitalises an ordinary lowercase name`() {
        assertEquals("Flour", smart.parseIngredient("flour").name)
        assertEquals("Apple", smart.parseIngredient("apple").name)
    }

    @Test
    fun `leaves an already-capitalised name alone`() {
        assertEquals("Flour", smart.parseIngredient("Flour").name)
    }

    // ---- Attached digits ----

    @Test
    fun `keeps a digit-leading name whole when the glued word is not a unit`() {
        assertEquals(SmartInput.ParsedIngredient("7up", null), smart.parseIngredient("7up"))
        assertEquals(SmartInput.ParsedIngredient("7-Eleven", null), smart.parseIngredient("7-Eleven"))
        assertEquals(SmartInput.ParsedIngredient("3M tape", null), smart.parseIngredient("3M tape"))
    }

    @Test
    fun `still reads a quantity when the glued word is a unit`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", "500 ml"), smart.parseIngredient("500ml Milk"))
        assertEquals(SmartInput.ParsedIngredient("Milk", "1 l"), smart.parseIngredient("1L Milk"))
        assertEquals(SmartInput.ParsedIngredient("Potatoes", "1.5 kg"), smart.parseIngredient("1.5kg potatoes"))
    }

    @Test
    fun `attached-digit rule does not affect space-separated quantities`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", "500 ml"), smart.parseIngredient("500 ml Milk"))
        assertEquals(SmartInput.ParsedIngredient("Eggs", "12"), smart.parseIngredient("12 Eggs"))
    }

    @Test
    fun `a comma after the digits is a separator not a glued word`() {
        assertEquals(SmartInput.ParsedIngredient("Aepfel", "10"), smart.parseIngredient("10, Aepfel"))
    }

    // ---- Nx multiplier ----

    @Test
    fun `reads Nx as a count`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", "2"), smart.parseIngredient("2x Milk"))
        assertEquals(SmartInput.ParsedIngredient("Eggs", "6"), smart.parseIngredient("6x Eggs"))
    }

    @Test
    fun `multiplier accepts a space and either case`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", "2"), smart.parseIngredient("2 x Milk"))
        assertEquals(SmartInput.ParsedIngredient("Milk", "2"), smart.parseIngredient("2X Milk"))
    }

    @Test
    fun `multiplier accepts the multiplication sign`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", "2"), smart.parseIngredient("2 \u00d7 Milk"))
    }

    @Test
    fun `multiplier count wins over a following quantity`() {
        assertEquals(SmartInput.ParsedIngredient("500g flour", "2"), smart.parseIngredient("2 x 500g flour"))
    }

    @Test
    fun `known limitation - a spaced leading digit is still read as a quantity`() {
        // Documents current behaviour (matches the web app): nothing syntactic separates
        // "7 Up" from "2 Apples", so the digit is taken as the count. "7up" is the escape hatch.
        assertEquals(SmartInput.ParsedIngredient("Up", "7"), smart.parseIngredient("7 Up"))
    }

    // ---- Multi-line input ----

    @Test
    fun `splitLines trims and drops blank lines across line-ending styles`() {
        assertEquals(listOf("Milk", "- Eggs", "Bread"), SmartInput.splitLines("  Milk \r\n\n- Eggs\nBread\n"))
        assertEquals(emptyList<String>(), SmartInput.splitLines(" \n \r\n"))
    }

    // ---- Checked import (web app 1.7.1) ----

    @Test
    fun `a ticked checkbox imports as checked`() {
        val p = smart.parseIngredient("[x] Milk")
        assertEquals("Milk", p.name)
        assertTrue(p.checked)
    }

    @Test
    fun `an empty checkbox is not checked`() {
        assertEquals(false, smart.parseIngredient("[ ] Milk").checked)
        assertEquals(false, smart.parseIngredient("Milk").checked)
    }

    @Test
    fun `a markdown checklist line imports as checked`() {
        val p = smart.parseIngredient("- [x] Milk")
        assertEquals("Milk", p.name)
        assertTrue(p.checked)
    }

    @Test
    fun `markers nest in either order`() {
        assertEquals(SmartInput.ParsedIngredient("Milk", null, checked = true), smart.parseIngredient("[x] - Milk"))
        assertEquals(SmartInput.ParsedIngredient("Milk", null, checked = false), smart.parseIngredient("- [ ] Milk"))
    }

    @Test
    fun `a ticked line keeps its quantity`() {
        assertEquals(SmartInput.ParsedIngredient("Aepfel", "10", checked = true), smart.parseIngredient("[x] 10, Aepfel"))
    }

    @Test
    fun `a checked line never merges into an open item`() {
        val existing = listOf(item(5, "Milk", quantity = "1"))
        val plan = smart.planAdd("[x] milk", emptyList(), existing, explicitAreaId = null)
        assertTrue(plan is SmartInput.AddPlan.Create)
        plan as SmartInput.AddPlan.Create
        assertTrue(plan.checked)
    }

    @Test
    fun `an unchecked line still merges`() {
        val existing = listOf(item(5, "Milk", quantity = "1"))
        assertTrue(smart.planAdd("[ ] milk", emptyList(), existing, explicitAreaId = null) is SmartInput.AddPlan.Merge)
    }
}
