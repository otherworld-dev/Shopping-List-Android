package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.guest.GuestLinkDeadException
import dev.otherworld.shoppinglist.data.guest.GuestPasswordNeededException
import dev.otherworld.shoppinglist.data.guest.GuestReadOnlyException
import dev.otherworld.shoppinglist.data.guest.GuestServerTroubleException

/** What the drain does after sending one change failed. */
internal sealed interface FailureAction {
    /** The share link stopped working: everything queued for it is dropped. */
    data class MarkDead(val shareId: Long) : FailureAction

    /** The owner made the link view only: everything queued for it is dropped. */
    data class MarkReadOnly(val shareId: Long) : FailureAction

    /** The link needs a password the app doesn't have: its changes wait for one. */
    data class MarkPasswordNeeded(val shareId: Long) : FailureAction

    /** Network down: the destination waits for the next drain, cooling down first if [backoff]. */
    data class Halt(val backoff: Boolean) : FailureAction

    /** The target is gone on the server: the change is dropped. */
    data object Discard : FailureAction

    /** The server is in trouble: the destination cools down, the change keeps its attempts. */
    data object Transient : FailureAction

    /** The server rejected the change: it uses up an attempt, and is dropped after the last. */
    data object CountAttempt : FailureAction
}

internal fun failureAction(e: Throwable?, d: Destination): FailureAction = when (e) {
    is GuestLinkDeadException -> FailureAction.MarkDead(e.shareId)
    is GuestReadOnlyException -> FailureAction.MarkReadOnly(e.shareId)
    is GuestPasswordNeededException -> FailureAction.MarkPasswordNeeded(e.shareId)
    // A 404 that isn't the app's own is the friend's server misbehaving, not a lost link.
    is GuestServerTroubleException -> FailureAction.Transient
    else -> when (SyncErrorPolicy.classify(e)) {
        // An unreachable friend's server can take up to a minute to time out, so it cools down
        // rather than stalling every drain; your own server just waits for the network.
        SyncErrorAction.HALT -> FailureAction.Halt(backoff = d is Destination.Guest)
        SyncErrorAction.DISCARD -> FailureAction.Discard
        SyncErrorAction.TRANSIENT -> FailureAction.Transient
        SyncErrorAction.COUNT_ATTEMPT -> FailureAction.CountAttempt
    }
}
