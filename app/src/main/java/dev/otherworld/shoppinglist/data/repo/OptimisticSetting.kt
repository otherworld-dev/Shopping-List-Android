package dev.otherworld.shoppinglist.data.repo

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * A server-kept on/off setting that changes on screen at once and goes straight to the server,
 * like the web app's switches: not queued, since it only changes how things look.
 *
 * Two races are handled. A refresh that read the server before a change landed is ignored
 * ([beginRefresh]/[applyRefresh], as the list sort does with localChangeWins), and a failed change
 * only goes back if nothing newer has been shown since.
 */
class OptimisticSetting(initial: Boolean, private val persist: (Boolean) -> Unit) {
    private val _value = MutableStateFlow(initial)
    val value: StateFlow<Boolean> = _value.asStateFlow()

    /** Bumped by every change the user makes, so a refresh can tell one happened mid-fetch. */
    private val edits = AtomicLong()

    /** Bumped by every value shown, so a failed change can tell something newer replaced it. */
    private val version = AtomicLong()

    /**
     * Shows [enabled] and has [send] tell the server. Leaving the screen doesn't cancel the
     * request (the switch already moved, so the change must land or visibly go back). When it
     * fails, the old value comes back and the failure is rethrown for the screen to show.
     */
    suspend fun set(enabled: Boolean, send: suspend (Boolean) -> Unit) {
        val before = _value.value
        if (before == enabled) return
        edits.incrementAndGet()
        _value.value = enabled
        val mine = version.incrementAndGet()
        withContext(NonCancellable) {
            try {
                send(enabled)
                persist(enabled)
            } catch (e: Exception) {
                if (version.get() == mine) {
                    _value.value = before
                    version.incrementAndGet()
                }
                throw e
            }
        }
    }

    /** Taken before a refresh asks the server, and handed back to [applyRefresh]. */
    fun beginRefresh(): Long = edits.get()

    /** The server's value, unless the user changed the setting while the refresh was out. */
    fun applyRefresh(server: Boolean, token: Long) {
        if (edits.get() != token) return
        _value.value = server
        version.incrementAndGet()
        persist(server)
    }

    /** On logout. */
    fun reset() {
        edits.incrementAndGet()
        _value.value = false
        version.incrementAndGet()
    }
}
