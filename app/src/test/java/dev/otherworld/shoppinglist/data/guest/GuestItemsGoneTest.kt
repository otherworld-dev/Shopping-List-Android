package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.domain.guest.GuestIds
import org.junit.Assert.assertEquals
import org.junit.Test

class GuestItemsGoneTest {

    private val milk = GuestIds.fromSeq(1)
    private val bread = GuestIds.fromSeq(2)

    @Test
    fun `an item the server no longer sends is gone`() {
        assertEquals(listOf(bread), guestItemsGone(listOf(milk, bread), serverIds = setOf(milk), pending = emptySet()))
    }

    @Test
    fun `an item with a queued change stays`() {
        assertEquals(emptyList<Long>(), guestItemsGone(listOf(milk), serverIds = emptySet(), pending = setOf(milk)))
    }

    @Test
    fun `an item added offline stays while its create is queued`() {
        assertEquals(emptyList<Long>(), guestItemsGone(listOf(-5L), serverIds = emptySet(), pending = setOf(-5L)))
    }

    @Test
    fun `an item added offline whose create was dropped is gone`() {
        assertEquals(listOf(-5L), guestItemsGone(listOf(-5L, milk), serverIds = setOf(milk), pending = emptySet()))
    }
}
