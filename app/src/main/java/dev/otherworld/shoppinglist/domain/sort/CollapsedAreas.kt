package dev.otherworld.shoppinglist.domain.sort

/**
 * Which shop-area groups a viewer has folded away on a list. Port of the web app's
 * `utils/collapsedAreas.ts` (1.8.0).
 *
 * This is a view preference, not list data: one person may fold away the areas they have
 * already walked past while someone else on the same shared list keeps them all open. So it
 * lives on the device ([dev.otherworld.shoppinglist.data.prefs.DisplayPrefs]), one entry per
 * list, and never goes to the server.
 */
object CollapsedAreas {

    /** A stable key for an area group. Items with no area form their own group. */
    fun areaKey(areaId: Long?): String = areaId?.toString() ?: "none"

    /** The set with [areaId]'s group folded if it was open, unfolded if it was folded. */
    fun toggled(current: Set<String>, areaId: Long?): Set<String> {
        val key = areaKey(areaId)
        return if (key in current) current - key else current + key
    }
}
