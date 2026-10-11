package dev.otherworld.shoppinglist.ui

/**
 * Whether start-up should reopen the list used last (issue #16). Only once per launch, so a
 * rotation or a restored process doesn't stack it twice; only over the lists screen, so Back
 * still goes there; never in the way of a share link being opened; and only when the
 * setting is on and a list has been opened before.
 */
internal fun shouldRestoreLastList(
    alreadyChecked: Boolean,
    startRoute: String,
    hasPendingLink: Boolean,
    enabled: Boolean,
    lastListId: Long?,
): Boolean = !alreadyChecked && startRoute == Routes.HOME && !hasPendingLink && enabled && lastListId != null
