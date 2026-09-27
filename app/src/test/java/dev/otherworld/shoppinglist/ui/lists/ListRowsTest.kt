package dev.otherworld.shoppinglist.ui.lists

import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.ListSections
import dev.otherworld.shoppinglist.domain.sort.SectionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListRowsTest {

    private fun list(id: Long, pinned: Boolean = false, owner: Boolean = true) =
        ShoppingListModel(id = id, title = "L$id", permission = 1, isOwner = owner, isPinned = pinned)

    private fun labels(rows: List<ListsRow>) = rows.map {
        when (it) {
            is ListsRow.Caption -> "#${it.textRes}"
            is ListsRow.Entry -> "${it.list.id}"
        }
    }

    @Test
    fun `captions follow the web sidebar`() {
        val rows = buildListRows(ListSections(listOf(list(1, pinned = true)), listOf(list(2)), listOf(list(3, owner = false))))
        assertEquals(
            listOf("#${R.string.lists_section_pinned}", "1", "#${R.string.lists_section_others}", "2", "#${R.string.lists_section_shared}", "3"),
            labels(rows),
        )
    }

    @Test
    fun `your lists get no caption without a pinned section above them`() {
        val rows = buildListRows(ListSections(emptyList(), listOf(list(2)), emptyList()))
        assertEquals(listOf("2"), labels(rows))
    }

    @Test
    fun `a list moves within its section`() {
        val rows = buildListRows(ListSections(emptyList(), listOf(list(1), list(2), list(3)), emptyList()))
        val moved = moveWithinSection(rows, 2, 0)!!
        assertEquals(listOf(3L, 1L, 2L), sectionOrder(moved, SectionKey.OWNED))
    }

    @Test
    fun `a list can't be moved onto a caption or into another section`() {
        val rows = buildListRows(ListSections(listOf(list(1, pinned = true)), listOf(list(2)), emptyList()))
        // rows: caption, 1, caption, 2
        assertNull(moveWithinSection(rows, 3, 2))
        assertNull(moveWithinSection(rows, 3, 1))
    }
}
