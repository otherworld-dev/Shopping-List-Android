package dev.otherworld.shoppinglist.domain.sort

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import java.text.Collator
import java.time.OffsetDateTime
import java.util.Locale

/**
 * How the items on a list are ordered, chosen in the list's menu. Port of the web app's
 * `utils/itemSort.ts` (1.8.0).
 *
 * Open items ([OpenSort]): grouped by shop area in drag order, grouped and A-Z inside each
 * area, or one flat A-Z list. Checked-off items ([BoughtSort]): in shop area order, A-Z, or
 * the item ticked last at the top.
 *
 * Like collapsed areas these are view preferences, not list data, so they live in
 * [dev.otherworld.shoppinglist.data.prefs.DisplayPrefs] and never go to the server, and they
 * never touch the drag order everyone on a shared list sees. Each is one choice for all
 * lists: someone who wants A to Z wants it everywhere.
 */
enum class OpenSort(val storageValue: String) {
    AREA("area"),
    AREA_ALPHA("areaAlpha"),
    ALPHA("alpha");

    companion object {
        /** A saved choice; anything unreadable or unknown counts as the default. */
        fun fromStorage(raw: String?): OpenSort = entries.firstOrNull { it.storageValue == raw } ?: AREA
    }
}

enum class BoughtSort(val storageValue: String) {
    AREA("area"),
    ALPHA("alpha"),
    RECENT("recent");

    companion object {
        fun fromStorage(raw: String?): BoughtSort = entries.firstOrNull { it.storageValue == raw } ?: AREA
    }
}

/** One shop-area group of open items; a null [area] is the uncategorized group. */
data class AreaGroup(
    val area: ShopAreaModel?,
    val items: List<ItemModel>,
)

/**
 * The open items as the list shows them. Grouped by area, the groups follow the order of
 * [areas] (pass them sorted the way the list shows them), and items with no area, or an area
 * the list no longer has, come last in a group of their own. A to Z is a single group with
 * no area, so the view shows it without a header.
 *
 * Sorting is stable, so items that compare equal keep their list order.
 */
fun groupOpenItems(items: List<ItemModel>, areas: List<ShopAreaModel>, sort: OpenSort, locale: Locale): List<AreaGroup> {
    if (items.isEmpty()) return emptyList()

    if (sort == OpenSort.ALPHA) {
        return listOf(AreaGroup(area = null, items = items.sortedWith(byName(locale))))
    }

    val grouped = LinkedHashMap<Long?, MutableList<ItemModel>>()
    for (item in items) grouped.getOrPut(item.shopAreaId) { mutableListOf() }.add(item)

    val result = mutableListOf<AreaGroup>()
    for (area in areas) {
        val areaItems = grouped.remove(area.id) ?: continue
        result += AreaGroup(area, areaItems)
    }

    val uncategorized = grouped.values.flatten()
    if (uncategorized.isNotEmpty()) result += AreaGroup(area = null, items = uncategorized)

    if (sort == OpenSort.AREA_ALPHA) {
        val compare = byName(locale)
        return result.map { it.copy(items = it.items.sortedWith(compare)) }
    }
    return result
}

/**
 * The checked-off items in the chosen order, as a new list. Sorting is stable, so items that
 * compare equal keep their list order.
 *
 * "Most recent first" uses updatedAt: the server stamps it when an item is ticked, and a
 * ticked item cannot be edited, so for these items it is the time they were ticked.
 */
fun sortBought(items: List<ItemModel>, sort: BoughtSort, areas: List<ShopAreaModel>, locale: Locale): List<ItemModel> =
    when (sort) {
        BoughtSort.AREA -> {
            val position = areas.withIndex().associate { (index, area) -> area.id to index }
            items.sortedBy { item -> item.shopAreaId?.let(position::get) ?: areas.size }
        }
        BoughtSort.ALPHA -> items.sortedWith(byName(locale))
        BoughtSort.RECENT -> items.sortedByDescending(::time)
    }

/**
 * Name order for the A-Z sorts: the language's collation, ignoring case and accents, with
 * numbers in names compared by value (so "9" sorts before "10") — the behaviour of the web
 * app's `Intl.Collator(language, { sensitivity: 'base', numeric: true })`.
 */
private fun byName(locale: Locale): Comparator<ItemModel> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> compareNatural(a.name, b.name, collator) }
}

private fun compareNatural(a: String, b: String, collator: Collator): Int {
    val runsA = digitRuns(a)
    val runsB = digitRuns(b)
    for (i in 0 until minOf(runsA.size, runsB.size)) {
        val x = runsA[i]
        val y = runsB[i]
        val compared = if (x[0].isDigit() && y[0].isDigit()) compareNumeric(x, y) else collator.compare(x, y)
        if (compared != 0) return compared
    }
    return runsA.size - runsB.size
}

/** Splits into alternating runs of digits and non-digits, so "AA 10" becomes ["AA ", "10"]. */
private fun digitRuns(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    val runs = mutableListOf<String>()
    var start = 0
    for (i in 1 until value.length) {
        if (value[i].isDigit() != value[start].isDigit()) {
            runs += value.substring(start, i)
            start = i
        }
    }
    runs += value.substring(start)
    return runs
}

/** Compares two all-digit runs by value without overflowing on long runs. */
private fun compareNumeric(x: String, y: String): Int {
    val a = x.trimStart('0').ifEmpty { "0" }
    val b = y.trimStart('0').ifEmpty { "0" }
    if (a.length != b.length) return a.length - b.length
    return a.compareTo(b)
}

/** An item's updatedAt as a comparable instant; anything unreadable sorts last. */
private fun time(item: ItemModel): Long =
    try {
        OffsetDateTime.parse(item.updatedAt).toInstant().toEpochMilli()
    } catch (_: Exception) {
        Long.MIN_VALUE
    }
