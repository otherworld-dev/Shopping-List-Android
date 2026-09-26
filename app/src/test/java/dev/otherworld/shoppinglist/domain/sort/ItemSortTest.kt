package dev.otherworld.shoppinglist.domain.sort

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.util.Locale

/** Port of the web app's `utils/itemSort.test.ts` (1.8.0). */
class ItemSortTest {

    private var nextId = 1L

    private fun item(
        name: String,
        shopAreaId: Long? = null,
        updatedAt: String? = "2026-09-01T10:00:00Z",
    ) = ItemModel(
        id = nextId++, listId = 1, name = name, quantity = null, unit = null,
        shopAreaId = shopAreaId, checked = false, checkedBy = null, sortOrder = 0,
        updatedAt = updatedAt,
    )

    private fun area(id: Long, name: String, color: String? = null) =
        ShopAreaModel(id = id, listId = 1, name = name, sortOrder = id.toInt(), color = color, keywords = emptyList())

    // Areas in the order the list shows them, which is not id order.
    private val dairy = 2L
    private val produce = 1L
    private val areas = listOf(area(dairy, "Dairy"), area(produce, "Produce"))

    private fun names(items: List<ItemModel>) = items.map { it.name }
    private fun groups(result: List<AreaGroup>) = result.map { it.area?.name to names(it.items) }

    private fun items() = listOf(
        item("milk", dairy),
        item("pears", produce),
        item("tape"),
        item("apples", produce),
        item("Cheese", dairy),
        item("batteries"),
    )

    // ---- groupOpenItems: by area ----

    @Test
    fun `groups by area in area order and keeps the drag order inside`() {
        assertEquals(
            listOf(
                "Dairy" to listOf("milk", "Cheese"),
                "Produce" to listOf("pears", "apples"),
                null to listOf("tape", "batteries"),
            ),
            groups(groupOpenItems(items(), areas, OpenSort.AREA, Locale.ENGLISH)),
        )
    }

    @Test
    fun `puts items of an area the list no longer has with the uncategorized ones`() {
        val result = groupOpenItems(listOf(item("soap", 99), item("tape")), areas, OpenSort.AREA, Locale.ENGLISH)
        assertEquals(listOf(null to listOf("soap", "tape")), groups(result))
    }

    @Test
    fun `leaves out areas with no items`() {
        assertEquals(
            listOf("Dairy" to listOf("milk")),
            groups(groupOpenItems(listOf(item("milk", dairy)), areas, OpenSort.AREA, Locale.ENGLISH)),
        )
    }

    @Test
    fun `carries the area on each group`() {
        val coloured = listOf(area(dairy, "Dairy", color = "#fff"))
        val group = groupOpenItems(listOf(item("milk", dairy)), coloured, OpenSort.AREA, Locale.ENGLISH).single()
        assertEquals(dairy, group.area?.id)
        assertEquals("#fff", group.area?.color)
    }

    // ---- groupOpenItems: by area, A to Z ----

    @Test
    fun `groups by area in area order and sorts by name inside`() {
        assertEquals(
            listOf(
                "Dairy" to listOf("Cheese", "milk"),
                "Produce" to listOf("apples", "pears"),
                null to listOf("batteries", "tape"),
            ),
            groups(groupOpenItems(items(), areas, OpenSort.AREA_ALPHA, Locale.ENGLISH)),
        )
    }

    // ---- groupOpenItems: A to Z ----

    @Test
    fun `is one group with no area sorted by name`() {
        val result = groupOpenItems(items(), areas, OpenSort.ALPHA, Locale.ENGLISH)
        assertEquals(1, result.size)
        assertNull(result[0].area)
        assertEquals(listOf("apples", "batteries", "Cheese", "milk", "pears", "tape"), names(result[0].items))
    }

    @Test
    fun `has no groups when there are no items whatever the sort`() {
        assertEquals(emptyList<AreaGroup>(), groupOpenItems(emptyList(), areas, OpenSort.AREA, Locale.ENGLISH))
        assertEquals(emptyList<AreaGroup>(), groupOpenItems(emptyList(), areas, OpenSort.AREA_ALPHA, Locale.ENGLISH))
        assertEquals(emptyList<AreaGroup>(), groupOpenItems(emptyList(), areas, OpenSort.ALPHA, Locale.ENGLISH))
    }

    @Test
    fun `does not change the list it was given`() {
        val given = items()
        groupOpenItems(given, areas, OpenSort.ALPHA, Locale.ENGLISH)
        assertEquals(listOf("milk", "pears", "tape", "apples", "Cheese", "batteries"), names(given))
    }

    // ---- A to Z order ----

    private fun alpha(list: List<ItemModel>, locale: Locale = Locale.ENGLISH) =
        names(groupOpenItems(list, emptyList(), OpenSort.ALPHA, locale)[0].items)

    @Test
    fun `ignores case`() {
        assertEquals(
            listOf("apples", "Bananas", "cherries"),
            alpha(listOf(item("cherries"), item("Bananas"), item("apples"))),
        )
    }

    @Test
    fun `sorts an accented letter with its base letter`() {
        assertEquals(
            listOf("dates", "éclairs", "fish"),
            alpha(listOf(item("fish"), item("éclairs"), item("dates"))),
        )
    }

    @Test
    fun `follows the language so Swedish puts a-umlaut after z`() {
        val list = { listOf(item("ägg"), item("zucchini"), item("bröd")) }
        assertEquals(listOf("bröd", "zucchini", "ägg"), alpha(list(), Locale.forLanguageTag("sv")))
        assertEquals(listOf("ägg", "bröd", "zucchini"), alpha(list(), Locale.ENGLISH))
    }

    @Test
    fun `sorts numbers in names by value`() {
        assertEquals(
            listOf("AA batteries 9", "AA batteries 10"),
            alpha(listOf(item("AA batteries 10"), item("AA batteries 9"))),
        )
    }

    @Test
    fun `keeps list order between items with the same name`() {
        val first = item("milk")
        val second = item("Milk")
        assertEquals(listOf(first, second), groupOpenItems(listOf(first, second), emptyList(), OpenSort.ALPHA, Locale.ENGLISH)[0].items)
        assertEquals(listOf(second, first), groupOpenItems(listOf(second, first), emptyList(), OpenSort.ALPHA, Locale.ENGLISH)[0].items)
    }

    // ---- sortBought ----

    @Test
    fun `follows the area order with items without an area last`() {
        val list = listOf(item("tape"), item("pears", produce), item("milk", dairy), item("apples", produce))
        assertEquals(listOf("milk", "pears", "apples", "tape"), names(sortBought(list, BoughtSort.AREA, areas, Locale.ENGLISH)))
    }

    @Test
    fun `treats an area the list no longer has like no area`() {
        val list = listOf(item("soap", 99), item("milk", dairy), item("tape"))
        assertEquals(listOf("milk", "soap", "tape"), names(sortBought(list, BoughtSort.AREA, areas, Locale.ENGLISH)))
    }

    @Test
    fun `sorts by name and ignores the area`() {
        val list = listOf(item("pears", produce), item("Cheese", dairy), item("apples"))
        assertEquals(listOf("apples", "Cheese", "pears"), names(sortBought(list, BoughtSort.ALPHA, areas, Locale.ENGLISH)))
    }

    @Test
    fun `puts the item ticked last at the top`() {
        val list = listOf(
            item("milk", null, "2026-09-01T10:00:00Z"),
            item("apples", null, "2026-09-01T12:00:00Z"),
            item("bread", null, "2026-09-01T11:00:00Z"),
        )
        assertEquals(listOf("apples", "bread", "milk"), names(sortBought(list, BoughtSort.RECENT, areas, Locale.ENGLISH)))
    }

    @Test
    fun `compares times not the text of the timestamps`() {
        val list = listOf(
            item("milk", null, "2026-09-01T10:00:00+00:00"),
            item("apples", null, "2026-09-01T11:30:00+02:00"),
        )
        // 11:30 at +02:00 is 09:30 UTC, so it is older than milk.
        assertEquals(listOf("milk", "apples"), names(sortBought(list, BoughtSort.RECENT, areas, Locale.ENGLISH)))
    }

    @Test
    fun `puts items with an unreadable time last`() {
        val list = listOf(item("milk", null, "not a date"), item("apples"))
        assertEquals(listOf("apples", "milk"), names(sortBought(list, BoughtSort.RECENT, areas, Locale.ENGLISH)))
        val missing = listOf(item("milk", null, null), item("apples"))
        assertEquals(listOf("apples", "milk"), names(sortBought(missing, BoughtSort.RECENT, areas, Locale.ENGLISH)))
    }

    @Test
    fun `keeps list order between items ticked at the same time`() {
        assertEquals(
            listOf("milk", "apples"),
            names(sortBought(listOf(item("milk"), item("apples")), BoughtSort.RECENT, areas, Locale.ENGLISH)),
        )
    }

    @Test
    fun `returns a new list and leaves the given one alone`() {
        val list = listOf(item("milk"), item("apples"))
        val sorted = sortBought(list, BoughtSort.ALPHA, areas, Locale.ENGLISH)
        assertNotSame(list, sorted)
        assertEquals(listOf("milk", "apples"), names(list))
    }

    // ---- saved choices (the storage-value mapping; persistence itself lives in DisplayPrefs) ----

    @Test
    fun `are by area when nothing was saved`() {
        assertEquals(OpenSort.AREA, OpenSort.fromStorage(null))
        assertEquals(BoughtSort.AREA, BoughtSort.fromStorage(null))
    }

    @Test
    fun `read back what was saved each on its own`() {
        assertEquals(OpenSort.AREA_ALPHA, OpenSort.fromStorage(OpenSort.AREA_ALPHA.storageValue))
        assertEquals(OpenSort.ALPHA, OpenSort.fromStorage(OpenSort.ALPHA.storageValue))
        assertEquals(BoughtSort.RECENT, BoughtSort.fromStorage(BoughtSort.RECENT.storageValue))
    }

    @Test
    fun `ignore a value they do not know`() {
        assertEquals(OpenSort.AREA, OpenSort.fromStorage("recent"))
        assertEquals(BoughtSort.AREA, BoughtSort.fromStorage("price"))
    }
}
