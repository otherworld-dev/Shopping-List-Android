package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.domain.guest.GuestIds

/** In-memory GuestIdLookup: local ids are handed out in order from GuestIds.BASE + 1. */
class FakeIds : GuestIdLookup {
    private val byKey = mutableMapOf<Triple<Long, String, Long>, Long>()
    private val remoteByLocal = mutableMapOf<Long, Long>()

    override suspend fun localId(shareId: Long, kind: String, remoteId: Long): Long =
        byKey.getOrPut(Triple(shareId, kind, remoteId)) {
            GuestIds.fromSeq(byKey.size + 1L).also { remoteByLocal[it] = remoteId }
        }

    override suspend fun remoteId(localId: Long): Long? = remoteByLocal[localId]
}
