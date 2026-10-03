package dev.otherworld.shoppinglist.data.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoShrinkTest {

    @Test
    fun `a large photo shrinks so its longest edge is 1280, keeping its shape`() {
        assertEquals(1280 to 960, shrunkSize(4000, 3000))
        assertEquals(960 to 1280, shrunkSize(3000, 4000))
    }

    @Test
    fun `a small photo is never made larger`() {
        assertEquals(800 to 600, shrunkSize(800, 600))
        assertEquals(1280 to 1280, shrunkSize(1280, 1280))
    }

    @Test
    fun `a very thin photo keeps at least a pixel`() {
        assertEquals(1280 to 1, shrunkSize(100_000, 10))
    }

    @Test
    fun `decodes at the largest power of two that keeps the edge at least 1280`() {
        assertEquals(2, decodeSampleSize(4000, 3000))
        assertEquals(4, decodeSampleSize(6000, 4000))
        assertEquals(1, decodeSampleSize(2000, 1500))
        assertEquals(1, decodeSampleSize(800, 600))
    }
}
