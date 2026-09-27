package dev.otherworld.shoppinglist.domain.sort

import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import java.time.OffsetDateTime
import java.util.Locale

/**
 * How a user's lists are ordered, chosen under Settings. Pinned lists come first, then their
 * own, then those shared with them; each section is sorted by the chosen mode. Port of the web
 * app's `utils/listSort.ts` and `utils/listReorder.ts` (1.10.0), which mirror the server.
 */
enum class ListSortMode(val storageValue: String) {
    UPDATED("updated"),
    ALPHA("alpha"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(raw: String?): ListSortMode = entries.firstOrNull { it.storageValue == raw } ?: UPDATED
    }
}

enum class SectionKey { PINNED, OWNED, SHARED }

data class ListSections(
    val pinned: List<ShoppingListModel>,
    val owned: List<ShoppingListModel>,
    val shared: List<ShoppingListModel>,
) {
    operator fun get(key: SectionKey): List<ShoppingListModel> = when (key) {
        SectionKey.PINNED -> pinned
        SectionKey.OWNED -> owned
        SectionKey.SHARED -> shared
    }
}

fun sortLists(lists: List<ShoppingListModel>, mode: ListSortMode, locale: Locale): ListSections {
    val collator = nameCollator(locale)
    val comparator = Comparator<ShoppingListModel> { a, b ->
        val byMode = when (mode) {
            ListSortMode.ALPHA -> compareNatural(a.title, b.title, collator)
            ListSortMode.CUSTOM -> byPosition(a, b)
            ListSortMode.UPDATED -> newestFirst(a, b)
        }
        if (byMode != 0) byMode else a.id.compareTo(b.id)
    }
    return ListSections(
        pinned = lists.filter { it.isPinned }.sortedWith(comparator),
        owned = lists.filter { !it.isPinned && it.isOwner }.sortedWith(comparator),
        shared = lists.filter { !it.isPinned && !it.isOwner }.sortedWith(comparator),
    )
}

/** What saving a drop needs: the section orders to send, in turn, and whether it switches to Custom. */
data class ReorderPlan(val saves: List<List<Long>>, val switchToCustom: Boolean)

/**
 * Switching to Custom must keep what the user sees, so every section's current order is saved
 * as positions before the setting changes; otherwise lists never placed would jump.
 */
fun planListReorder(sections: ListSections, key: SectionKey, order: List<Long>, current: ListSortMode): ReorderPlan {
    if (current == ListSortMode.CUSTOM) return ReorderPlan(listOf(order), switchToCustom = false)
    val saves = SectionKey.entries
        .map { if (it == key) order else sections[it].map { list -> list.id } }
        .filter { it.isNotEmpty() }
    return ReorderPlan(saves, switchToCustom = true)
}

/** Each non-empty section's ids in the order it shows now. */
fun freezeOrder(sections: ListSections): List<List<Long>> =
    SectionKey.entries.map { sections[it].map { list -> list.id } }.filter { it.isNotEmpty() }

/**
 * Choosing Custom from Settings keeps a saved custom order: only sections where no list has
 * been placed yet are saved as they show, so they don't jump. Mirrors the web app's freezeUnplaced.
 */
fun freezeUnplaced(sections: ListSections): List<List<Long>> =
    SectionKey.entries
        .map { sections[it] }
        .filter { lists -> lists.isNotEmpty() && lists.all { it.position == null } }
        .map { lists -> lists.map { it.id } }

/** Null means made on this phone and not yet synced, so the newest; unreadable means the oldest. */
private fun listTime(list: ShoppingListModel): Long {
    val raw = list.updatedAt ?: return Long.MAX_VALUE
    return try {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Long.MIN_VALUE
    }
}

private fun newestFirst(a: ShoppingListModel, b: ShoppingListModel): Int = listTime(b).compareTo(listTime(a))

private fun byPosition(a: ShoppingListModel, b: ShoppingListModel): Int {
    val pa = a.position
    val pb = b.position
    return when {
        pa == null && pb == null -> newestFirst(a, b)
        pa == null -> -1
        pb == null -> 1
        else -> pa.compareTo(pb)
    }
}
