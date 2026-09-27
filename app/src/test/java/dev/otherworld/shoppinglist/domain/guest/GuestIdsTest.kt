package dev.otherworld.shoppinglist.domain.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestIdsTest {

    @Test
    fun `guest ids start at 2 to the 40`() {
        assertEquals(1_099_511_627_776L, GuestIds.BASE)
        assertEquals(GuestIds.BASE + 7, GuestIds.fromSeq(7))
    }

    @Test
    fun `server ids and temp ids are never guest ids`() {
        assertFalse(GuestIds.isGuest(42))
        assertFalse(GuestIds.isGuest(Int.MAX_VALUE.toLong()))
        assertFalse(GuestIds.isGuest(-System.currentTimeMillis()))
        assertFalse(GuestIds.isGuest(0))
        assertTrue(GuestIds.isGuest(GuestIds.fromSeq(1)))
    }
}
