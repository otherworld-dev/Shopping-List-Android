package dev.otherworld.shoppinglist.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

// The lists screen's counts (#28): which lists' items to fetch after the server counts them.
class StaleItemsTest {

    @Test
    fun `a list whose count matches the phone is left alone`() {
        assertEquals(emptyList<Long>(), listsWithStaleItems(mapOf(1L to 3), mapOf(1L to 3), emptySet()))
    }

    @Test
    fun `a list changed elsewhere is fetched`() {
        assertEquals(listOf(1L), listsWithStaleItems(mapOf(1L to 4), mapOf(1L to 3), emptySet()))
    }

    @Test
    fun `a list never opened here is fetched, unless it has nothing left to get`() {
        assertEquals(listOf(2L), listsWithStaleItems(mapOf(1L to 0, 2L to 5), emptyMap(), emptySet()))
    }

    @Test
    fun `everything ticked on the server but not on the phone is fetched`() {
        assertEquals(listOf(1L), listsWithStaleItems(mapOf(1L to 0), mapOf(1L to 2), emptySet()))
    }

    @Test
    fun `a list with changes still queued keeps the phone's count`() {
        assertEquals(emptyList<Long>(), listsWithStaleItems(mapOf(1L to 4), mapOf(1L to 3), setOf(1L)))
    }
}
