package dev.otherworld.shoppinglist.ui

import dev.otherworld.shoppinglist.data.prefs.StartList

/**
 * The list a fresh start opens (issues #16 and #20), or null for the lists screen. Only once per
 * launch, so a rotation or a restored process doesn't stack it twice; only over the lists
 * screen, so Back still goes there; and never in the way of a share link being opened.
 */
internal fun listToOpenAtStart(
    alreadyChecked: Boolean,
    startRoute: String,
    hasPendingLink: Boolean,
    startList: StartList,
    lastListId: Long?,
): Long? {
    if (alreadyChecked || startRoute != Routes.HOME || hasPendingLink) return null
    return when (startList) {
        StartList.AllLists -> null
        StartList.LastUsed -> lastListId
        is StartList.Chosen -> startList.listId
    }
}
