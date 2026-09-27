package dev.otherworld.shoppinglist.ui.lists

import androidx.annotation.StringRes
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.ListSections
import dev.otherworld.shoppinglist.domain.sort.SectionKey

/** A list opened from a share link, with where it lives and how the link is doing. */
data class GuestListEntry(val list: ShoppingListModel, val host: String, val state: String)

/** The lists screen as rows (captions and lists), so it can be drag-reordered. */
internal sealed interface ListsRow {
    val key: String

    data class Caption(@StringRes val textRes: Int) : ListsRow {
        override val key get() = "caption-$textRes"
    }

    data class Entry(val section: SectionKey, val list: ShoppingListModel, val alt: Boolean) : ListsRow {
        override val key get() = "list-${list.id}"
    }

    data class Guest(val entry: GuestListEntry, val alt: Boolean) : ListsRow {
        override val key get() = "guest-${entry.list.id}"
    }
}

/**
 * Pinned lists first; your own lists next, captioned Others only when there's a Pinned
 * section above them; then Shared with me; then lists from share links, last and not
 * draggable. Mirrors the web app's sidebar.
 */
internal fun buildListRows(sections: ListSections, guests: List<GuestListEntry> = emptyList()): List<ListsRow> {
    val rows = mutableListOf<ListsRow>()
    fun add(section: SectionKey, caption: Int?) {
        val lists = sections[section]
        if (lists.isEmpty()) return
        caption?.let { rows += ListsRow.Caption(it) }
        lists.forEachIndexed { i, list -> rows += ListsRow.Entry(section, list, alt = i % 2 == 1) }
    }
    add(SectionKey.PINNED, R.string.lists_section_pinned)
    add(SectionKey.OWNED, if (sections.pinned.isNotEmpty()) R.string.lists_section_others else null)
    add(SectionKey.SHARED, R.string.lists_section_shared)
    if (guests.isNotEmpty()) {
        rows += ListsRow.Caption(R.string.lists_section_links)
        guests.forEachIndexed { i, entry -> rows += ListsRow.Guest(entry, alt = i % 2 == 1) }
    }
    return rows
}

/** The rows with one list moved, or null when the move would leave its section or land on a caption. */
internal fun moveWithinSection(rows: List<ListsRow>, from: Int, to: Int): List<ListsRow>? {
    val moving = rows.getOrNull(from) as? ListsRow.Entry ?: return null
    val target = rows.getOrNull(to) as? ListsRow.Entry ?: return null
    if (moving.section != target.section) return null
    return rows.toMutableList().apply { add(to, removeAt(from)) }
}

/** A section's list ids in the order the rows show them. */
internal fun sectionOrder(rows: List<ListsRow>, section: SectionKey): List<Long> =
    rows.filterIsInstance<ListsRow.Entry>().filter { it.section == section }.map { it.list.id }
