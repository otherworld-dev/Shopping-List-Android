package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.domain.guest.GuestIds
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnListsGoneTest {

    @Test
    fun `a list the server no longer sends is gone`() {
        assertEquals(listOf(3L), ownListsGone(listOf(1L, 3L), serverIds = setOf(1L), pending = emptySet()))
    }

    @Test
    fun `lists made offline, lists with queued changes and guest lists stay`() {
        val guest = GuestIds.fromSeq(1)
        assertEquals(
            emptyList<Long>(),
            ownListsGone(listOf(-5L, 7L, guest), serverIds = emptySet(), pending = setOf(7L)),
        )
    }
}
