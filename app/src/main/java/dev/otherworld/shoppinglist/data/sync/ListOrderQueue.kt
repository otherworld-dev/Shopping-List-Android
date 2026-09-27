package dev.otherworld.shoppinglist.data.sync

/** The payload with a synced list's real id in place of its temp id, or null if it isn't in it. */
internal fun ListOrderPayload.remapped(oldId: Long, newId: Long): ListOrderPayload? =
    if (oldId in listIds) copy(listIds = listIds.map { if (it == oldId) newId else it }) else null

/** A list made offline and deleted before it synced never reached the server, so it's left out. */
internal fun ListOrderPayload.idsToSend(): List<Long> = listIds.filter { it > 0 }

/**
 * Whether a refresh keeps this phone's copy rather than the server's response. A change still
 * queued on either side of the fetch is newer than the response, and so is one made while the
 * fetch was in flight ([editsBefore] to [editsAfter]), even if it was sent and dequeued before
 * the response arrived.
 */
internal fun localChangeWins(queuedBefore: Boolean, editsBefore: Long, editsAfter: Long, queuedAfter: Boolean): Boolean =
    queuedBefore || editsAfter != editsBefore || queuedAfter

/**
 * A refresh from the server keeps this phone's position while its own reorder is newer than the
 * response (see [localChangeWins]); otherwise the list would snap back until the next refresh.
 */
internal fun positionAfterRefresh(serverPosition: Int?, localPosition: Int?, localWins: Boolean): Int? =
    if (localWins) localPosition else serverPosition
