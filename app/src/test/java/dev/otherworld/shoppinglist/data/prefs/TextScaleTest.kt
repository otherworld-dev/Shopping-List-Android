package dev.otherworld.shoppinglist.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {

    @Test
    fun `reads back every size it stores`() {
        TextScale.entries.forEach { assertEquals(it, TextScale.fromStorage(it.storageValue)) }
    }

    @Test
    fun `nothing stored, or something unknown, is the default size`() {
        assertEquals(TextScale.DEFAULT, TextScale.fromStorage(null))
        assertEquals(TextScale.DEFAULT, TextScale.fromStorage("huge"))
    }

    @Test
    fun `the default leaves the phone's own font size alone`() {
        assertEquals(1f, TextScale.DEFAULT.factor)
    }

    @Test
    fun `sizes go from smallest to largest`() {
        val factors = TextScale.entries.map { it.factor }
        assertEquals(factors.sorted(), factors)
    }
}
