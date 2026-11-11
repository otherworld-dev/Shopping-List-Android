package dev.otherworld.shoppinglist.data.sync

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ServerAppStateTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `only a 404 from the lists call means the server app is missing`() {
        assertTrue(isServerAppMissing(http(404)))
        assertFalse(isServerAppMissing(http(500)))
        assertFalse(isServerAppMissing(http(403)))
        assertFalse(isServerAppMissing(IOException()))
    }

    @Test
    fun `nothing is known until the lists call answers`() {
        val s = ServerAppState()
        assertFalse(s.installed("a"))
        assertFalse(s.missing("a"))
    }

    @Test
    fun `a 404 marks the app missing and stops the queue being sent`() {
        val s = ServerAppState().answered("a", null).answered("a", http(404))
        assertTrue(s.missing("a"))
        assertFalse(s.installed("a"))
    }

    @Test
    fun `an answer once it is installed clears the missing message`() {
        val s = ServerAppState().answered("a", http(404)).answered("a", null)
        assertFalse(s.missing("a"))
        assertTrue(s.installed("a"))
    }

    @Test
    fun `being offline or a server error changes nothing`() {
        val missing = ServerAppState().answered("a", http(404))
        assertTrue(missing.answered("a", IOException()).missing("a"))
        assertTrue(missing.answered("a", http(503)).missing("a"))
        val installed = ServerAppState().answered("a", null)
        assertTrue(installed.answered("a", IOException()).installed("a"))
    }

    @Test
    fun `what is known is for one account only`() {
        val s = ServerAppState().answered("a", null).answered("b", http(404))
        assertFalse(s.installed("b"))
        assertFalse(s.missing("a"))
        assertTrue(s.installed("a"))
        assertTrue(s.missing("b"))
        assertFalse(s.missing(null))
        assertFalse(s.installed(null))
    }
}
