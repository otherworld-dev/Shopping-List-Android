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
    fun `a size multiplies the phone's own font scale`() {
        assertEquals(1.3f, TextScale.DEFAULT.fontScaleOn(1.3f), 0.001f)
        assertEquals(1.3f, TextScale.LARGER.fontScaleOn(1f), 0.001f)
        assertEquals(1.17f, TextScale.LARGER.fontScaleOn(0.9f), 0.001f)
    }

    @Test
    fun `the result stops at 2x, where Android stops scaling text gently`() {
        assertEquals(2f, TextScale.LARGE.fontScaleOn(1.8f), 0.001f)
        assertEquals(2f, TextScale.LARGER.fontScaleOn(2f), 0.001f)
    }

    @Test
    fun `smaller text still works on the phone's largest font`() {
        assertEquals(1.8f, TextScale.SMALL.fontScaleOn(2f), 0.001f)
    }

    @Test
    fun `sizes go from smallest to largest`() {
        val factors = TextScale.entries.map { it.factor }
        assertEquals(factors.sorted(), factors)
    }
}
