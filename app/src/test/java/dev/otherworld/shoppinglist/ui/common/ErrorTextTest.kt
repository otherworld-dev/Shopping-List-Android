package dev.otherworld.shoppinglist.ui.common

import dev.otherworld.shoppinglist.R
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * Errors reach the screen as translatable messages, never as an exception's own text, which is
 * English and often technical ("Unable to resolve host …").
 */
class ErrorTextTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `a failed connection reads as being offline`() {
        assertEquals(UiText(R.string.error_offline), errorText(UnknownHostException("cloud.example.com")))
        assertEquals(UiText(R.string.error_offline), errorText(IOException("timeout")))
    }

    @Test
    fun `a refusal reads as having no permission`() {
        assertEquals(UiText(R.string.error_no_permission), errorText(http(403)))
    }

    @Test
    fun `server trouble reads as the server having a problem`() {
        assertEquals(UiText(R.string.error_server), errorText(http(500)))
        assertEquals(UiText(R.string.error_server), errorText(http(503)))
    }

    @Test
    fun `anything else is a general failure`() {
        assertEquals(UiText(R.string.error_generic), errorText(http(400)))
        assertEquals(UiText(R.string.error_generic), errorText(IllegalStateException("boom")))
    }

    @Test
    fun `carries the arguments for a message that has them`() {
        val text = UiText(R.string.notice_item_moved, "Milk", "Weekend")
        assertEquals(listOf("Milk", "Weekend"), text.args)
    }
}
