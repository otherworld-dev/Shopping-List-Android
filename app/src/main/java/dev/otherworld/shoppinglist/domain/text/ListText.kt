package dev.otherworld.shoppinglist.domain.text

import dev.otherworld.shoppinglist.domain.model.ItemModel

/**
 * Renders a list as plain text, one item per line, in the same shape the add box accepts when
 * you paste into it — so a list copied out of the app pastes back in as the same items, and
 * drops into a chat message unchanged. A port of the web app's `utils/listText.ts`.
 *
 * Checked items are left out: they have been bought, and the point of copying a list is to
 * carry the outstanding part of it somewhere else.
 */
fun formatListAsText(items: List<ItemModel>): String =
    items
        .filter { !it.checked }
        .joinToString("\n") { item ->
            val quantity = item.quantity?.takeIf { it.isNotEmpty() }
            val unit = item.unit?.takeIf { it.isNotEmpty() }
            buildList {
                // A bare "1" is the implicit default, so writing it back adds noise. With a
                // unit it is meaningful ("1 l"), so it stays.
                if (quantity != null && (quantity != "1" || unit != null)) add(quantity)
                if (unit != null) add(unit)
                add(item.name)
            }.joinToString(" ")
        }
