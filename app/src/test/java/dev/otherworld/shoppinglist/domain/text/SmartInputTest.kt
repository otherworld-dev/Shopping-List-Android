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
}
