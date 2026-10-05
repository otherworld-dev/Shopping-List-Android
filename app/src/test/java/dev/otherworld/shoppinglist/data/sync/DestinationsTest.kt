package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.domain.guest.GuestIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationsTest {

    private val guestList = GuestIds.fromSeq(1)
    private val leftList = GuestIds.fromSeq(2)
    private val shares = mapOf(guestList to 4L)

    private fun m(seq: Long, listId: Long) =
        MutationEntity(seq = seq, entity = "item", type = "check", targetId = 1, listId = listId, payload = "{}")

    @Test
    fun `each change goes to its list's server`() {
        assertEquals(Destination.Own, destinationOf(m(1, 12), shares))
        assertEquals(Destination.Own, destinationOf(m(1, -3), shares))
        assertEquals(Destination.Own, destinationOf(m(1, 0), shares))
        assertEquals(Destination.Guest(4), destinationOf(m(1, guestList), shares))
        assertEquals(Destination.Orphan, destinationOf(m(1, leftList), shares))
    }

    @Test
    fun `a server that's down doesn't hold up the others`() {
        val queue = listOf(m(1, guestList), m(2, 12), m(3, guestList))
        val next = firstRunnable(queue, { destinationOf(it, shares) }, setOf(Destination.Guest(4)))
        assertEquals(2L, next?.seq)
    }

    @Test
    fun `the user's own changes waiting leaves guest changes free`() {
        val queue = listOf(m(1, 12), m(2, guestList))
        assertEquals(2L, firstRunnable(queue, { destinationOf(it, shares) }, setOf(Destination.Own))?.seq)
    }

    @Test
    fun `nothing runs when every destination waits`() {
        val queue = listOf(m(1, 12), m(2, guestList))
        assertNull(firstRunnable(queue, { destinationOf(it, shares) }, setOf(Destination.Own, Destination.Guest(4))))
    }

    @Test
    fun `order within a destination is kept`() {
        val queue = listOf(m(1, guestList), m(2, guestList))
        assertEquals(1L, firstRunnable(queue, { destinationOf(it, shares) }, emptySet())?.seq)
    }
}
