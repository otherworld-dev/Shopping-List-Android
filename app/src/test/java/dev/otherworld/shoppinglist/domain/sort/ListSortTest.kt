package dev.otherworld.shoppinglist.domain.sort

import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Port of the web app's `utils/listSort.test.ts` and `utils/listReorder.test.ts` (1.10.0). */
class ListSortTest {

    private fun list(
        id: Long,
        title: String,
        updatedAt: String?,
        isOwner: Boolean = true,
        isPinned: Boolean = false,
        position: Int? = null,
    ) = ShoppingListModel(
        id = id, title = title, permission = 1, isOwner = isOwner,
        isPinned = isPinned, position = position, updatedAt = updatedAt,
    )

    private fun ids(sections: ListSections) =
        listOf(sections.pinned, sections.owned, sections.shared).map { s -> s.map { it.id } }

    @Test
    fun `splits pinned, owned and shared whatever the sort`() {
        val lists = listOf(
            list(1, "Shared", "2026-09-26T12:00:00Z", isOwner = false),
            list(2, "Owned", "2026-09-20T12:00:00Z"),
            list(3, "Pinned", "2026-09-01T12:00:00Z", isPinned = true),
        )
        ListSortMode.entries.forEach { mode ->
            assertEquals(mode.name, listOf(listOf(3L), listOf(2L), listOf(1L)), ids(sortLists(lists, mode, Locale.ENGLISH)))
        }
    }

    @Test
    fun `puts the most recently updated first`() {
        val lists = listOf(list(1, "Old", "2026-09-01T12:00:00Z"), list(2, "New", "2026-09-26T12:00:00Z"))
        assertEquals(listOf(2L, 1L), ids(sortLists(lists, ListSortMode.UPDATED, Locale.ENGLISH))[1])
    }

    @Test
    fun `a list made on this phone and not yet synced counts as the newest`() {
        val lists = listOf(list(1, "Synced", "2026-09-26T12:00:00Z"), list(-5, "Made offline", null))
        assertEquals(listOf(-5L, 1L), ids(sortLists(lists, ListSortMode.UPDATED, Locale.ENGLISH))[1])
    }

    @Test
    fun `an unreadable time sorts last and ties go by id`() {
        val lists = listOf(list(5, "Same", "not a date"), list(4, "Same", "not a date"), list(6, "Dated", "2026-09-01T12:00:00Z"))
        assertEquals(listOf(6L, 4L, 5L), ids(sortLists(lists, ListSortMode.UPDATED, Locale.ENGLISH))[1])
    }

    @Test
    fun `sorts A to Z in the language, ignoring case and reading numbers by value`() {
        val lists = listOf(
            list(1, "weekend 10", "2026-09-26T12:00:00Z"),
            list(2, "Weekend 9", "2026-09-01T12:00:00Z"),
            list(3, "apples", "2026-09-10T12:00:00Z"),
        )
        assertEquals(listOf(3L, 2L, 1L), ids(sortLists(lists, ListSortMode.ALPHA, Locale.ENGLISH))[1])
    }

    @Test
    fun `puts unplaced lists first, newest first, then the rest by position`() {
        val lists = listOf(
            list(1, "Second", "2026-09-01T12:00:00Z", position = 1),
            list(2, "First", "2026-09-02T12:00:00Z", position = 0),
            list(3, "New", "2026-09-26T12:00:00Z"),
            list(4, "Older new", "2026-09-20T12:00:00Z"),
        )
        assertEquals(listOf(3L, 4L, 2L, 1L), ids(sortLists(lists, ListSortMode.CUSTOM, Locale.ENGLISH))[1])
    }

    @Test
    fun `reads the three sorts and anything else as recently updated`() {
        assertEquals(ListSortMode.CUSTOM, ListSortMode.fromStorage("custom"))
        assertEquals(ListSortMode.ALPHA, ListSortMode.fromStorage("alpha"))
        assertEquals(ListSortMode.UPDATED, ListSortMode.fromStorage("price"))
        assertEquals(ListSortMode.UPDATED, ListSortMode.fromStorage(null))
    }

    private val sections = ListSections(
        pinned = listOf(list(1, "P", "2026-09-01T12:00:00Z", isPinned = true)),
        owned = listOf(list(2, "A", "2026-09-01T12:00:00Z"), list(3, "B", "2026-09-01T12:00:00Z")),
        shared = emptyList(),
    )

    @Test
    fun `saves only the dropped section when already on Custom`() {
        assertEquals(
            ReorderPlan(listOf(listOf(3L, 2L)), switchToCustom = false),
            planListReorder(sections, SectionKey.OWNED, listOf(3L, 2L), ListSortMode.CUSTOM),
        )
    }

    @Test
    fun `keeps every other section as it shows when a drop switches to Custom`() {
        assertEquals(
            ReorderPlan(listOf(listOf(1L), listOf(3L, 2L)), switchToCustom = true),
            planListReorder(sections, SectionKey.OWNED, listOf(3L, 2L), ListSortMode.UPDATED),
        )
    }

    @Test
    fun `freezing saves each non-empty section as it shows`() {
        assertEquals(listOf(listOf(1L), listOf(2L, 3L)), freezeOrder(sections))
    }

    @Test
    fun `choosing Custom keeps a section that already has positions`() {
        val placed = ListSections(
            pinned = emptyList(),
            owned = listOf(list(2, "A", "2026-09-01T12:00:00Z", position = 0), list(3, "B", "2026-09-01T12:00:00Z")),
            shared = emptyList(),
        )
        assertEquals(emptyList<List<Long>>(), freezeUnplaced(placed))
    }

    @Test
    fun `choosing Custom saves a never-placed section as it shows, and skips empty ones`() {
        assertEquals(listOf(listOf(1L), listOf(2L, 3L)), freezeUnplaced(sections))
    }
}
