package dev.otherworld.shoppinglist.ui.items

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.CollapsedAreas
import dev.otherworld.shoppinglist.domain.sort.OpenSort
import dev.otherworld.shoppinglist.domain.sort.groupOpenItems
import dev.otherworld.shoppinglist.domain.sort.sortBought
import java.util.Locale

// ---- Flattened display rows (headers + items) so the list can be drag-reordered ----

internal sealed interface Row {
    val key: String

    data class AreaHeaderRow(
        val area: ShopAreaModel?,
        val count: Int,
        val collapsed: Boolean = false,
        /** Ids of the items folded away under this header, in display order (see [planReorder]). */
        val hiddenIds: List<Long> = emptyList(),
    ) : Row {
        override val key get() = "h-${area?.id ?: -1L}"
    }

    data class ItemRowData(
        val item: ItemModel,
        val draggable: Boolean,
        val alt: Boolean,
        val showAreaName: Boolean,
    ) : Row {
        override val key get() = if (item.checked) "c-${item.id}" else "i-${item.id}"
    }

    data class CheckedHeaderRow(val count: Int) : Row {
        override val key get() = "checked-header"
    }
}

internal fun buildRows(
    items: List<ItemModel>,
    areas: List<ShopAreaModel>,
    openSort: OpenSort,
    boughtSort: BoughtSort,
    collapsedKeys: Set<String>,
    locale: Locale,
): List<Row> {
    val rows = mutableListOf<Row>()
    val orderedAreas = areas.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val unchecked = items.filterNot { it.checked }.sortedBy { it.sortOrder }
    val groups = groupOpenItems(unchecked, orderedAreas, openSort, locale)
    // Dragging sets the order and area by hand, which only means something when the list
    // shows that order. In the A-to-Z modes the sort decides where an item goes.
    val draggable = openSort == OpenSort.AREA
    groups.forEach { group ->
        // A lone uncategorized group gets no header: nothing to name and nothing to fold —
        // the flat A-to-Z sort, and lists whose open items all have no area.
        val hasHeader = group.area != null || groups.size > 1
        val collapsed = hasHeader && CollapsedAreas.areaKey(group.area?.id) in collapsedKeys
        if (hasHeader) {
            rows += Row.AreaHeaderRow(
                area = group.area,
                count = group.items.size,
                collapsed = collapsed,
                hiddenIds = if (collapsed) group.items.map { it.id } else emptyList(),
            )
        }
        if (!collapsed) {
            group.items.forEachIndexed { i, item ->
                // Grouped rows sit under their area's header; only the flat A-to-Z list
                // needs the area's name on each row.
                rows += Row.ItemRowData(item, draggable = draggable, alt = i % 2 == 1, showAreaName = openSort == OpenSort.ALPHA)
            }
        }
    }
    val checked = sortBought(items.filter { it.checked }, boughtSort, orderedAreas, locale)
    if (checked.isNotEmpty()) {
        rows += Row.CheckedHeaderRow(checked.size)
        // Checked items are listed together across areas, so the area name still carries
        // meaning on each row.
        checked.forEachIndexed { i, item -> rows += Row.ItemRowData(item, draggable = false, alt = i % 2 == 1, showAreaName = true) }
    }
    return rows
}

/** What a drop means for the server: the new order of the open items, and any area changes. */
internal data class ReorderPlan(
    val orderedIds: List<Long>,
    /** Items dropped under a different area's header, paired with that area's id. */
    val areaMoves: List<Pair<ItemModel, Long?>>,
)

/**
 * Reads the rows after a drag back into a [ReorderPlan]. An item's area is taken from the
 * header it now sits under, which is why the headers must stay ordinary list rows.
 */
internal fun planReorder(rows: List<Row>): ReorderPlan {
    val orderedIds = mutableListOf<Long>()
    val areaMoves = mutableListOf<Pair<ItemModel, Long?>>()
    var currentAreaId: Long? = null
    rows.forEach { row ->
        when (row) {
            is Row.AreaHeaderRow -> {
                currentAreaId = row.area?.id
                // A folded area's items aren't on screen, but they keep their place — and
                // their order — in the list the server stores.
                orderedIds += row.hiddenIds
            }
            is Row.ItemRowData -> if (row.draggable) {
                orderedIds += row.item.id
                if (row.item.shopAreaId != currentAreaId) areaMoves += row.item to currentAreaId
            }
            is Row.CheckedHeaderRow -> currentAreaId = null
        }
    }
    return ReorderPlan(orderedIds, areaMoves)
}
