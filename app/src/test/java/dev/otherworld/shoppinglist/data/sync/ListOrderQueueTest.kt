package dev.otherworld.shoppinglist.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a refresh keeps the phone's position while its reorder waits`() {
        assertEquals(1, positionAfterRefresh(serverPosition = 5, localPosition = 1, reorderPending = true))
        assertNull(positionAfterRefresh(serverPosition = 5, localPosition = null, reorderPending = true))
    }

    @Test
    fun `otherwise the server's position wins`() {
        assertEquals(5, positionAfterRefresh(serverPosition = 5, localPosition = 1, reorderPending = false))
    }
}
