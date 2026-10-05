package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.guest.GuestLinkDeadException
import dev.otherworld.shoppinglist.data.guest.GuestPasswordNeededException
import dev.otherworld.shoppinglist.data.guest.GuestReadOnlyException
import dev.otherworld.shoppinglist.data.guest.GuestServerTroubleException
import dev.otherworld.shoppinglist.data.guest.httpError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class FailureActionTest {

    private val guest = Destination.Guest(4)

    @Test
    fun `a dead link is marked dead`() {
        assertEquals(FailureAction.MarkDead(4), failureAction(GuestLinkDeadException(4), guest))
    }

    @Test
    fun `a link made view only is marked read only`() {
        assertEquals(FailureAction.MarkReadOnly(4), failureAction(GuestReadOnlyException(4), guest))
    }

    @Test
    fun `a link that needs a password is marked so`() {
        assertEquals(FailureAction.MarkPasswordNeeded(4), failureAction(GuestPasswordNeededException(4), guest))
    }

    @Test
    fun `a friend's server in trouble backs off without counting an attempt`() {
        assertEquals(FailureAction.Transient, failureAction(GuestServerTroubleException(4), guest))
    }

    @Test
    fun `no network halts a friend's server with a backoff`() {
        assertEquals(FailureAction.Halt(backoff = true), failureAction(IOException(), guest))
    }

    @Test
    fun `no network halts your own server without a backoff`() {
        assertEquals(FailureAction.Halt(backoff = false), failureAction(IOException(), Destination.Own))
    }

    @Test
    fun `a 404 discards the change on either destination`() {
        assertEquals(FailureAction.Discard, failureAction(httpError(404), Destination.Own))
        assertEquals(FailureAction.Discard, failureAction(httpError(404), guest))
    }

    @Test
    fun `server errors and rate limits are transient on either destination`() {
        for (code in listOf(500, 503, 429)) {
            assertEquals(FailureAction.Transient, failureAction(httpError(code), Destination.Own))
            assertEquals(FailureAction.Transient, failureAction(httpError(code), guest))
        }
    }

    @Test
    fun `a rejection counts an attempt on either destination`() {
        assertEquals(FailureAction.CountAttempt, failureAction(httpError(400), Destination.Own))
        assertEquals(FailureAction.CountAttempt, failureAction(httpError(403), guest))
        assertEquals(FailureAction.CountAttempt, failureAction(IllegalStateException(), Destination.Own))
    }
}
