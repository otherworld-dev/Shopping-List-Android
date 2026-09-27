package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.domain.guest.GuestIds

/** Where a queued change is sent. Each drains on its own, so one being down holds up no other. */
sealed interface Destination {
    data object Own : Destination
    data class Guest(val shareId: Long) : Destination

    /** A change for a guest list that has since been left; it's dropped. */
    data object Orphan : Destination
}

/** [guestListShares] maps each guest list's id to its share id. */
internal fun destinationOf(m: MutationEntity, guestListShares: Map<Long, Long>): Destination = when {
    !GuestIds.isGuest(m.listId) -> Destination.Own
    else -> guestListShares[m.listId]?.let { Destination.Guest(it) } ?: Destination.Orphan
}

/** The oldest change whose destination isn't waiting. */
internal fun firstRunnable(
    queue: List<MutationEntity>,
    destinationOf: (MutationEntity) -> Destination,
    blocked: Set<Destination>,
): MutationEntity? = queue.firstOrNull { destinationOf(it) !in blocked }
