package dev.otherworld.shoppinglist.ui.settings

import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.ui.lists.ownListsShown
import java.text.Collator
import java.util.Locale

/**
 * The lists that can be opened on start: the ones the lists screen shows (own, shared, from a
 * share link that's still there, or on the phone), A to Z so one is easy to find.
 */
internal fun startListChoices(
    lists: List<ShoppingListModel>,
    shareIds: Set<Long>,
    loggedIn: Boolean,
    locale: Locale,
): List<ShoppingListModel> {
    val collator = Collator.getInstance(locale)
    val linked = lists.filter { it.guestShareId in shareIds }
    return (ownListsShown(lists, loggedIn) + linked).sortedWith { a, b -> collator.compare(a.title, b.title) }
}
