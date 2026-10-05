package dev.otherworld.shoppinglist.ui.items

import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.photo.PhotoDecodeException
import dev.otherworld.shoppinglist.data.photo.PhotoTooLargeException
import dev.otherworld.shoppinglist.ui.common.UiText
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/** The web app's photo messages: 413 is too large, 415 is not an image, anything else failed. */
class PhotoErrorsTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `too large, from the server or before sending`() {
        assertEquals(UiText(R.string.error_photo_too_large), photoUploadErrorText(http(413)))
        assertEquals(UiText(R.string.error_photo_too_large), photoUploadErrorText(PhotoTooLargeException()))
    }

    @Test
    fun `not a picture, from the server or the phone`() {
        assertEquals(UiText(R.string.error_photo_unsupported), photoUploadErrorText(http(415)))
        assertEquals(UiText(R.string.error_photo_unsupported), photoUploadErrorText(PhotoDecodeException()))
    }

    @Test
    fun `a refusal reads as having no permission`() {
        assertEquals(UiText(R.string.error_no_permission), photoUploadErrorText(http(403)))
        assertEquals(UiText(R.string.error_no_permission), photoRemoveErrorText(http(403)))
    }

    @Test
    fun `a lost connection reads as being offline`() {
        assertEquals(UiText(R.string.error_photo_offline), photoUploadErrorText(IOException("reset")))
        assertEquals(UiText(R.string.error_photo_offline), photoRemoveErrorText(UnknownHostException("cloud.example.com")))
    }

    @Test
    fun `anything else is a failed upload or removal`() {
        assertEquals(UiText(R.string.error_photo_upload_failed), photoUploadErrorText(http(500)))
        assertEquals(UiText(R.string.error_photo_remove_failed), photoRemoveErrorText(http(500)))
    }

    @Test
    fun `only a 404 means the item is gone`() {
        assertTrue(isItemGone(http(404)))
        assertFalse(isItemGone(http(500)))
        assertFalse(isItemGone(IOException()))
    }
}
