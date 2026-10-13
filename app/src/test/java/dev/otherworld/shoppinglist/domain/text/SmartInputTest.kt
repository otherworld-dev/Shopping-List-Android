package dev.otherworld.shoppinglist.domain.text

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `reads the short forms of piece and pack (#15)`() {
        assertEquals(SmartInput.ParsedIngredient("Eggs", "3 pcs"), smart.parseIngredient("3 pcs eggs"))
        assertEquals(SmartInput.ParsedIngredient("Avocado", "1 pc"), smart.parseIngredient("1 pc avocado"))
        assertEquals(SmartInput.ParsedIngredient("Noodles", "2 pkg"), smart.parseIngredient("2 pkg noodles"))
    }

    @Test
    fun `reads decagrams (#15)`() {
        assertEquals(SmartInput.ParsedIngredient("Ham", "25 dag"), smart.parseIngredient("25 dag ham"))
        assertEquals(SmartInput.ParsedIngredient("Cheese", "10 dkg"), smart.parseIngredient("10 dkg cheese"))
        assertEquals(SmartInput.ParsedIngredient("Ham", "25 dag"), smart.parseIngredient("25dag ham"))
    }

    @Test
    fun `reads German amounts on a German phone (#15)`() {
        val de = SmartInput.forLanguage("de")
        assertEquals(SmartInput.ParsedIngredient("Schinken", "25 dag"), de.parseIngredient("25 dag Schinken"))
        assertEquals(SmartInput.ParsedIngredient("Käse", "10 dkg"), de.parseIngredient("10 dkg Käse"))
        assertEquals(SmartInput.ParsedIngredient("Eier", "3 stk"), de.parseIngredient("3 Stk Eier"))
        assertEquals(SmartInput.ParsedIngredient("Eier", "3 stk."), de.parseIngredient("3 Stk. Eier"))
        assertEquals(SmartInput.ParsedIngredient("Nudeln", "2 pkg"), de.parseIngredient("2 Pkg Nudeln"))
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

    @Test
    fun `pluralize leaves uncountable groceries alone`() {
        for (name in listOf("Milk", "Bread", "Butter", "Flour", "Sugar", "Coffee", "Fish", "Pasta", "Cheese")) {
            assertEquals(name, smart.pluralizeName(name))
        }
    }

    @Test
    fun `pluralize reads the last word, so a qualified mass noun stays singular`() {
        assertEquals("Oat milk", smart.pluralizeName("Oat milk"))
        assertEquals("Olive oil", smart.pluralizeName("Olive oil"))
        assertEquals("Ice cream", smart.pluralizeName("Ice cream"))
    }

    @Test
    fun `typed plural of an uncountable noun still matches`() {
        assertEquals(smart.normalizeName("Milk"), smart.normalizeName("Milks"))
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
    fun `plan keeps an uncountable name when crossing from one to many`() {
        val existing = listOf(item(5, "Milk", quantity = "1"))
        val plan = smart.planAdd("milk", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Merge
        assertEquals("2", plan.quantity)
        assertEquals("Milk", plan.newName ?: existing.single().name)
    }

    @Test
    fun `plan leaves the amount off a name on its own, for the server to fill in (#15)`() {
        val plan = smart.planAdd("rice", emptyList(), emptyList(), explicitAreaId = null) as SmartInput.AddPlan.Create
        assertEquals("Rice", plan.name)
        assertNull(plan.quantity)
    }

    @Test
    fun `plan still counts a name on its own as one more when merging`() {
        val existing = listOf(item(5, "Rice", quantity = "500 g"))
        val plan = smart.planAdd("rice", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Merge
        assertEquals("500 g + 1", plan.quantity)
    }

    @Test
    fun `merging adds up the short forms of a unit with the long ones (#15)`() {
        assertEquals("5 pcs", smart.mergeQuantities("3 pcs", "2 pieces"))
        assertEquals("35 dag", smart.mergeQuantities("25 dag", "10 dkg"))
        assertEquals("3 pkg", smart.mergeQuantities("2 pkg", "1 pack"))
    }

    @Test
    fun `plan brings back a ticked item rather than adding another`() {
        val existing = listOf(item(5, "Apple", quantity = "2", checked = true))
        val plan = smart.planAdd("apples", emptyList(), existing, explicitAreaId = null)
        assertEquals(SmartInput.AddPlan.Recycle(existing.single(), quantity = null), plan)
    }

    @Test
    fun `plan gives a brought back item the amount typed`() {
        val existing = listOf(item(5, "Rice", quantity = "1 kg", checked = true))
        val plan = smart.planAdd("500 g rice", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Recycle
        assertEquals("500 g", plan.quantity)
    }

    @Test
    fun `plan merges into an open item before bringing back a ticked one`() {
        val existing = listOf(item(5, "Milk", checked = true), item(6, "Milk", quantity = "1"))
        val plan = smart.planAdd("milk", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Merge
        assertEquals(6L, plan.target.id)
    }

    @Test
    fun `plan brings back the first of several ticked copies`() {
        val existing = listOf(item(5, "Milk", checked = true), item(6, "Milk", checked = true))
        val plan = smart.planAdd("milk", emptyList(), existing, explicitAreaId = null) as SmartInput.AddPlan.Recycle
        assertEquals(5L, plan.target.id)
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
    fun `a checked line never brings back a ticked item`() {
        val existing = listOf(item(5, "Milk", checked = true))
        val plan = smart.planAdd("[x] milk", emptyList(), existing, explicitAreaId = null)
        assertTrue(plan is SmartInput.AddPlan.Create)
    }

    @Test
    fun `an unchecked line still merges`() {
        val existing = listOf(item(5, "Milk", quantity = "1"))
        assertTrue(smart.planAdd("[ ] milk", emptyList(), existing, explicitAreaId = null) is SmartInput.AddPlan.Merge)
    }

    @Test
    fun `the search key is the name it would add, without the amount`() {
        assertEquals("milk", smart.searchKey("2 l milk"))
    }

    @Test
    fun `the search key folds case and accents like area matching`() {
        assertEquals("creme fraiche", smart.searchKey("Crème Fraîche"))
    }

    @Test
    fun `a blank box has no search key`() {
        assertEquals("", smart.searchKey("   "))
        assertEquals("", smart.searchKey(""))
    }

    @Test
    fun `an amount with no name yet has no search key`() {
        assertEquals("", smart.searchKey("2"))
        assertEquals("", smart.searchKey("2 l"))
        assertEquals("", smart.searchKey("1.5 kg"))
    }

    @Test
    fun `a name that starts with digits is still searched for`() {
        assertEquals("7up", smart.searchKey("7up"))
    }

    // ---- A list's default area (server app 1.12.0) ----

    private val defaultAreas = listOf(area(1, "Produce", listOf("banana"), 0), area(2, "Other", emptyList(), 1))

    @Test
    fun `a name no keyword matches goes to the list's default area`() {
        val plan = smart.planAdd("batteries", defaultAreas, emptyList(), explicitAreaId = null, defaultAreaId = 2L)
        plan as SmartInput.AddPlan.Create
        assertEquals(2L, plan.shopAreaId)
        // Nobody chose it, so the server mustn't learn a keyword from it.
        assertFalse(plan.areaExplicit)
        // And it is only where the phone shows the item: the server applies its own default.
        assertTrue(plan.areaIsDefault)
    }

    @Test
    fun `an area from a keyword or a pick is not marked as the default`() {
        val detected = smart.planAdd("bananas", defaultAreas, emptyList(), explicitAreaId = null, defaultAreaId = 1L)
        val picked = smart.planAdd("batteries", defaultAreas, emptyList(), explicitAreaId = 2L, defaultAreaId = 2L)
        assertFalse((detected as SmartInput.AddPlan.Create).areaIsDefault)
        assertFalse((picked as SmartInput.AddPlan.Create).areaIsDefault)
    }

    @Test
    fun `a keyword match beats the default area`() {
        val plan = smart.planAdd("bananas", defaultAreas, emptyList(), explicitAreaId = null, defaultAreaId = 2L)
        assertEquals(1L, (plan as SmartInput.AddPlan.Create).shopAreaId)
    }

    @Test
    fun `a picked area beats the default area`() {
        val plan = smart.planAdd("batteries", defaultAreas, emptyList(), explicitAreaId = 1L, defaultAreaId = 2L)
        plan as SmartInput.AddPlan.Create
        assertEquals(1L, plan.shopAreaId)
        assertTrue(plan.areaExplicit)
    }

    @Test
    fun `a list with no default area leaves the item uncategorised`() {
        val plan = smart.planAdd("batteries", defaultAreas, emptyList(), explicitAreaId = null, defaultAreaId = null)
        assertNull((plan as SmartInput.AddPlan.Create).shopAreaId)
    }

    @Test
    fun `a default that is no longer one of the list's areas is ignored`() {
        // The area was deleted and this copy of the list hasn't caught up yet.
        val plan = smart.planAdd("batteries", defaultAreas, emptyList(), explicitAreaId = null, defaultAreaId = 99L)
        assertNull((plan as SmartInput.AddPlan.Create).shopAreaId)
    }
}
