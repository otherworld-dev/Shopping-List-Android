package dev.otherworld.shoppinglist.data.sync

import retrofit2.HttpException
import java.io.IOException

/** What the drain loop should do with a failed mutation. */
enum class SyncErrorAction {
    /** Network is down — stop draining; connectivity regain re-triggers. */
    HALT,

    /** The target is gone on the server — drop the mutation, it's benign. */
    DISCARD,

    /**
     * The server is having a moment (5xx, rate limiting) — retry later WITHOUT burning one of
     * the mutation's attempts. A restarting server must never cost queued user data.
     */
    TRANSIENT,

    /** The server rejected the request itself — count an attempt, give up after MAX_ATTEMPTS. */
    COUNT_ATTEMPT,
}

object SyncErrorPolicy {
    fun classify(e: Throwable?): SyncErrorAction = when {
        e is IOException -> SyncErrorAction.HALT
        e is HttpException && e.code() == 404 -> SyncErrorAction.DISCARD
        e is HttpException && (e.code() in 500..599 || e.code() == 429) -> SyncErrorAction.TRANSIENT
        else -> SyncErrorAction.COUNT_ATTEMPT
    }
}

/**
 * Escalating cooldown between failed drains. Every add/edit calls requestSync(), so a burst
 * (a multi-line paste) would otherwise hammer a failing server several times within a second —
 * and, for counted errors, burn all of a mutation's attempts before the server can recover.
 * Doubles from [baseMs] up to [capMs]; any success resets it.
 */
class SyncBackoff(private val baseMs: Long = 2_000, private val capMs: Long = 60_000) {
    private var delayMs = 0L
    private var readyAt = 0L

    fun isReady(now: Long): Boolean = now >= readyAt

    fun recordFailure(now: Long) {
        delayMs = if (delayMs == 0L) baseMs else (delayMs * 2).coerceAtMost(capMs)
        readyAt = now + delayMs
    }

    fun recordSuccess() {
        delayMs = 0L
        readyAt = 0L
    }
}
