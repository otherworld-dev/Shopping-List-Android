package dev.otherworld.shoppinglist.domain.sort

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of the pure parts of the web app's `utils/collapsedAreas.ts` (1.8.0). */
class CollapsedAreasTest {

    @Test
    fun `items with no area form their own group key`() {
        assertEquals("none", CollapsedAreas.areaKey(null))
        assertEquals("5", CollapsedAreas.areaKey(5L))
    }

    @Test
    fun `toggling folds an open area and unfolds a folded one`() {
        assertEquals(setOf("3"), CollapsedAreas.toggled(emptySet(), 3L))
        assertEquals(emptySet<String>(), CollapsedAreas.toggled(setOf("3"), 3L))
        assertEquals(setOf("3", "none"), CollapsedAreas.toggled(setOf("3"), null))
    }

    @Test
    fun `toggling leaves the given set alone`() {
        val given = setOf("3")
        CollapsedAreas.toggled(given, 3L)
        assertEquals(setOf("3"), given)
    }
}
