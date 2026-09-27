package dev.otherworld.shoppinglist.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListOrderQueueTest {

    @Test
    fun `a queued order takes the real id of a list created offline`() {
        assertEquals(ListOrderPayload(listOf(4L, 42L, 7L)), ListOrderPayload(listOf(4L, -3L, 7L)).remapped(-3L, 42L))
    }

    @Test
    fun `an order without that list is left alone`() {
        assertNull(ListOrderPayload(listOf(4L, 7L)).remapped(-3L, 42L))
    }

    @Test
    fun `lists that never reached the server are left out`() {
        assertEquals(listOf(4L, 7L), ListOrderPayload(listOf(4L, -3L, 7L)).idsToSend())
    }

    @Test
    fun `a refresh keeps the phone's position while its reorder is newer`() {
        assertEquals(1, positionAfterRefresh(serverPosition = 5, localPosition = 1, localWins = true))
        assertNull(positionAfterRefresh(serverPosition = 5, localPosition = null, localWins = true))
    }

    @Test
    fun `otherwise the server's position wins`() {
        assertEquals(5, positionAfterRefresh(serverPosition = 5, localPosition = 1, localWins = false))
    }

    @Test
    fun `the server wins when nothing changed during the fetch`() {
        assertFalse(localChangeWins(queuedBefore = false, editsBefore = 3, editsAfter = 3, queuedAfter = false))
    }

    @Test
    fun `a change made during the fetch wins, even once it has been sent and dequeued`() {
        assertTrue(localChangeWins(queuedBefore = false, editsBefore = 3, editsAfter = 4, queuedAfter = false))
    }

    @Test
    fun `a change queued before or after the fetch wins`() {
        assertTrue(localChangeWins(queuedBefore = true, editsBefore = 3, editsAfter = 3, queuedAfter = false))
        assertTrue(localChangeWins(queuedBefore = false, editsBefore = 3, editsAfter = 3, queuedAfter = true))
    }
}
