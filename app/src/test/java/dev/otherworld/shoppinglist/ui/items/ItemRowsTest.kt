package dev.otherworld.shoppinglist.ui.items

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.OpenSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The item screen's display rows: which headers show, what folding hides, and how a drag
 * turns back into the order the server stores. Header rules follow the web app's
 * `ListView.vue` (1.8.0).
 */
class ItemRowsTest {

    private var nextId = 1L

    private fun item(name: String, areaId: Long? = null, checked: Boolean = false) = ItemModel(
        id = nextId++, listId = 1, name = name, quantity = null, unit = null,
        shopAreaId = areaId, checked = checked, checkedBy = null, sortOrder = nextId.toInt(),
    )

    private val dairy = ShopAreaModel(id = 2, listId = 1, name = "Dairy", sortOrder = 0, color = null, keywords = emptyList())
    private val produce = ShopAreaModel(id = 1, listId = 1, name = "Produce", sortOrder = 1, color = null, keywords = emptyList())
    private val areas = listOf(produce, dairy)

    private fun rows(
        items: List<ItemModel>,
        openSort: OpenSort = OpenSort.AREA,
        collapsed: Set<String> = emptySet(),
        filter: String = "",
    ) = buildRows(items, areas, openSort, BoughtSort.AREA, collapsed, Locale.ENGLISH, filter)

    /** Rows as short labels: "#Dairy", "milk", "#checked". */
    private fun labels(rows: List<Row>) = rows.map {
        when (it) {
            is Row.AreaHeaderRow -> "#" + (it.area?.name ?: "other")
            is Row.CheckedHeaderRow -> "#checked"
            is Row.ItemRowData -> it.item.name
        }
    }

    @Test
    fun `groups open items under area headers in the list's area order`() {
        val list = listOf(item("apples", produce.id), item("milk", dairy.id), item("tape"))
        assertEquals(listOf("#Dairy", "milk", "#Produce", "apples", "#other", "tape"), labels(rows(list)))
    }

    @Test
    fun `a lone uncategorized group gets no header`() {
        assertEquals(listOf("tape", "soap"), labels(rows(listOf(item("tape"), item("soap")))))
    }

    @Test
    fun `A to Z is one list with no headers showing each item's area`() {
        val result = rows(listOf(item("milk", dairy.id), item("apples", produce.id)), OpenSort.ALPHA)
        assertEquals(listOf("apples", "milk"), labels(result))
        assertTrue(result.filterIsInstance<Row.ItemRowData>().all { it.showAreaName })
    }

    @Test
    fun `only the by-area sort lets items be dragged`() {
        val list = listOf(item("milk", dairy.id))
        assertTrue(rows(list, OpenSort.AREA).filterIsInstance<Row.ItemRowData>().single().draggable)
        assertFalse(rows(list, OpenSort.AREA_ALPHA).filterIsInstance<Row.ItemRowData>().single().draggable)
        assertFalse(rows(list, OpenSort.ALPHA).filterIsInstance<Row.ItemRowData>().single().draggable)
    }

    @Test
    fun `a folded area keeps its header and count but hides its items`() {
        val list = listOf(item("milk", dairy.id), item("cheese", dairy.id), item("apples", produce.id))
        val result = rows(list, collapsed = setOf("2"))
        assertEquals(listOf("#Dairy", "#Produce", "apples"), labels(result))
        val header = result.first() as Row.AreaHeaderRow
        assertTrue(header.collapsed)
        assertEquals(2, header.count)
    }

    @Test
    fun `the uncategorized group folds under the none key`() {
        val list = listOf(item("milk", dairy.id), item("tape"))
        assertEquals(listOf("#Dairy", "milk", "#other"), labels(rows(list, collapsed = setOf("none"))))
    }

    @Test
    fun `a lone uncategorized group is never hidden even if it was folded before`() {
        assertEquals(listOf("tape"), labels(rows(listOf(item("tape")), collapsed = setOf("none"))))
    }

    @Test
    fun `folding never hides checked-off items`() {
        val list = listOf(item("milk", dairy.id), item("butter", dairy.id, checked = true))
        assertEquals(listOf("#Dairy", "#checked", "butter"), labels(rows(list, collapsed = setOf("2"))))
    }

    @Test
    fun `a drop keeps a folded area's hidden items in the stored order`() {
        val milk = item("milk", dairy.id)
        val cheese = item("cheese", dairy.id)
        val apples = item("apples", produce.id)
        val pears = item("pears", produce.id)
        val shown = rows(listOf(milk, cheese, apples, pears), collapsed = setOf("2"))
        // Drag pears above apples.
        val dropped = shown.toMutableList().apply { add(2, removeAt(3)) }
        assertEquals(listOf(milk.id, cheese.id, pears.id, apples.id), planReorder(dropped).orderedIds)
    }

    @Test
    fun `a drop under another area's header moves the item there`() {
        val milk = item("milk", dairy.id)
        val apples = item("apples", produce.id)
        val shown = rows(listOf(milk, apples))
        // Drag apples up under the Dairy header.
        val dropped = shown.toMutableList().apply { add(1, removeAt(3)) }
        val plan = planReorder(dropped)
        assertEquals(listOf(apples.id, milk.id), plan.orderedIds)
        assertEquals(listOf(apples to dairy.id), plan.areaMoves)
    }

    @Test
    fun `a drop under the no-area header takes the item out of its area`() {
        val milk = item("milk", dairy.id)
        val tape = item("tape")
        val shown = rows(listOf(milk, tape))
        // Drag milk down to the end, under the header of the items with no area.
        val dropped = shown.toMutableList().apply { add(3, removeAt(1)) }
        assertEquals(listOf("#Dairy", "#other", "tape", "milk"), labels(dropped))
        assertEquals(listOf(milk to null), planReorder(dropped).areaMoves)
    }

    @Test
    fun `a drop above the first header leaves the item's area alone`() {
        val milk = item("milk", dairy.id)
        val apples = item("apples", produce.id)
        val shown = rows(listOf(milk, apples))
        // Drag apples to the very top, above the Dairy header.
        val dropped = shown.toMutableList().apply { add(0, removeAt(3)) }
        val plan = planReorder(dropped)
        assertEquals(listOf(apples.id, milk.id), plan.orderedIds)
        assertEquals(emptyList<Pair<ItemModel, Long?>>(), plan.areaMoves)
    }

    @Test
    fun `a drop among the ticked items leaves the item's area alone`() {
        val milk = item("milk", dairy.id)
        val butter = item("butter", dairy.id, checked = true)
        val shown = rows(listOf(milk, butter))
        // Drag milk down past the ticked items' header.
        val dropped = shown.toMutableList().apply { add(2, removeAt(1)) }
        assertEquals(listOf("#Dairy", "#checked", "milk", "butter"), labels(dropped))
        assertEquals(emptyList<Pair<ItemModel, Long?>>(), planReorder(dropped).areaMoves)
    }

    @Test
    fun `a filter keeps only the items whose name contains it`() {
        val list = listOf(item("milk", dairy.id), item("oat milk"), item("apples", produce.id))
        assertEquals(listOf("#Dairy", "milk", "#other", "oat milk"), labels(rows(list, filter = "milk")))
    }

    @Test
    fun `a filter matches names regardless of case and accents`() {
        val list = listOf(item("Crème fraîche", dairy.id), item("apples", produce.id))
        assertEquals(listOf("#Dairy", "Crème fraîche"), labels(rows(list, filter = "creme")))
    }

    @Test
    fun `a filter finds ticked items so they can be unticked`() {
        val list = listOf(item("milk", dairy.id, checked = true), item("apples", produce.id))
        assertEquals(listOf("#checked", "milk"), labels(rows(list, filter = "milk")))
    }

    @Test
    fun `a filter shows the matches inside a folded area`() {
        val list = listOf(item("milk", dairy.id), item("cheese", dairy.id))
        val result = rows(list, collapsed = setOf("2"), filter = "milk")
        assertEquals(listOf("#Dairy", "milk"), labels(result))
        assertFalse((result.first() as Row.AreaHeaderRow).collapsed)
    }

    @Test
    fun `nothing can be dragged while filtering`() {
        val list = listOf(item("milk", dairy.id), item("oat milk"))
        assertTrue(rows(list, filter = "milk").filterIsInstance<Row.ItemRowData>().none { it.draggable })
    }

    @Test
    fun `a filter that matches nothing leaves no rows`() {
        assertEquals(emptyList<String>(), labels(rows(listOf(item("milk", dairy.id)), filter = "bread")))
    }

    @Test
    fun `a blank filter changes nothing`() {
        val list = listOf(item("milk", dairy.id), item("apples", produce.id, checked = true))
        assertEquals(rows(list), rows(list, filter = "  "))
    }
}
