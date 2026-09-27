package dev.otherworld.shoppinglist.data.sync

/** The payload with a synced list's real id in place of its temp id, or null if it isn't in it. */
internal fun ListOrderPayload.remapped(oldId: Long, newId: Long): ListOrderPayload? =
    if (oldId in listIds) copy(listIds = listIds.map { if (it == oldId) newId else it }) else null

/** A list made offline and deleted before it synced never reached the server, so it's left out. */
internal fun ListOrderPayload.idsToSend(): List<Long> = listIds.filter { it > 0 }

/**
 * A refresh from the server keeps this phone's position while its own reorder is still waiting
 * to be sent; otherwise the list would snap back until the reorder synced.
 */
internal fun positionAfterRefresh(serverPosition: Int?, localPosition: Int?, reorderPending: Boolean): Int? =
    if (reorderPending) localPosition else serverPosition
