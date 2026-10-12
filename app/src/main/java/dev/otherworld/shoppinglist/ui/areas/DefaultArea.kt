package dev.otherworld.shoppinglist.ui.areas

import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel

/** The list's default area as Manage areas shows it; [area] is null for none. */
data class DefaultAreaState(val area: ShopAreaModel?)

/**
 * Whether a default area can be chosen for [list] from this phone. It is the list's own
 * setting on the server, so it needs a server that keeps one ([supported], app 1.12.0 and
 * later) and a list the user may edit there: not one opened from a link, which lives on
 * someone else's server, and not one kept on the phone, which has no areas at all.
 */
internal fun canSetDefaultArea(supported: Boolean, list: ShoppingListModel?): Boolean =
    supported && list != null && list.canWrite && !list.isGuest && !list.isLocal
