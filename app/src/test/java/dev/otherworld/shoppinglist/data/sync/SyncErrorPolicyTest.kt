package dev.otherworld.shoppinglist.data.sync

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SyncErrorPolicyTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `a network error halts the drain`() {
        assertEquals(SyncErrorAction.HALT, SyncErrorPolicy.classify(IOException("down")))
    }

    @Test
    fun `a 404 is discarded as benign`() {
        assertEquals(SyncErrorAction.DISCARD, SyncErrorPolicy.classify(http(404)))
    }

    @Test
    fun `server errors are transient and never burn an attempt`() {
        for (code in listOf(500, 502, 503, 504)) {
            assertEquals("HTTP $code", SyncErrorAction.TRANSIENT, SyncErrorPolicy.classify(http(code)))
        }
    }

    @Test
    fun `rate limiting is transient`() {
        assertEquals(SyncErrorAction.TRANSIENT, SyncErrorPolicy.classify(http(429)))
    }

    @Test
    fun `client errors count an attempt`() {
        for (code in listOf(400, 403, 409, 422)) {
            assertEquals("HTTP $code", SyncErrorAction.COUNT_ATTEMPT, SyncErrorPolicy.classify(http(code)))
        }
    }

    @Test
    fun `unexpected exceptions count an attempt`() {
        assertEquals(SyncErrorAction.COUNT_ATTEMPT, SyncErrorPolicy.classify(IllegalStateException("bug")))
    }
}

class SyncBackoffTest {

    @Test
    fun `starts ready`() {
        assertTrue(SyncBackoff().isReady(now = 0))
    }

    @Test
    fun `a failure blocks until the base delay has passed`() {
        val b = SyncBackoff(baseMs = 2_000, capMs = 60_000)
        b.recordFailure(now = 10_000)
        assertFalse(b.isReady(now = 11_999))
        assertTrue(b.isReady(now = 12_000))
    }

    @Test
    fun `repeated failures double the delay up to the cap`() {
        val b = SyncBackoff(baseMs = 2_000, capMs = 8_000)
        b.recordFailure(now = 0)
        b.recordFailure(now = 2_000)
        b.recordFailure(now = 6_000)
        b.recordFailure(now = 14_000) // would be 16s, capped at 8s
        assertFalse(b.isReady(now = 21_999))
        assertTrue(b.isReady(now = 22_000))
    }

    @Test
    fun `success resets the delay`() {
        val b = SyncBackoff(baseMs = 2_000, capMs = 60_000)
        b.recordFailure(now = 0)
        b.recordFailure(now = 2_000)
        b.recordSuccess()
        assertTrue(b.isReady(now = 2_001))
        b.recordFailure(now = 3_000)
        assertFalse(b.isReady(now = 4_999))
        assertTrue(b.isReady(now = 5_000))
    }
}
