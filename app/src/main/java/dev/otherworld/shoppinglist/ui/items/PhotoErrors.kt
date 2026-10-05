package dev.otherworld.shoppinglist.ui.items

import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.photo.PhotoDecodeException
import dev.otherworld.shoppinglist.data.photo.PhotoTooLargeException
import dev.otherworld.shoppinglist.ui.common.UiText
import retrofit2.HttpException
import java.io.IOException

/** What a failed photo upload means, as the web app tells it (413 too large, 415 not an image). */
fun photoUploadErrorText(error: Throwable): UiText = when {
    error is PhotoTooLargeException -> UiText(R.string.error_photo_too_large)
    error is PhotoDecodeException -> UiText(R.string.error_photo_unsupported)
    error is HttpException && error.code() == 413 -> UiText(R.string.error_photo_too_large)
    error is HttpException && error.code() == 415 -> UiText(R.string.error_photo_unsupported)
    error is HttpException && error.code() == 403 -> UiText(R.string.error_no_permission)
    // The connection dropped, or went before the phone noticed it had.
    error is IOException -> UiText(R.string.error_photo_offline)
    else -> UiText(R.string.error_photo_upload_failed)
}

fun photoRemoveErrorText(error: Throwable): UiText = when {
    error is HttpException && error.code() == 403 -> UiText(R.string.error_no_permission)
    error is IOException -> UiText(R.string.error_photo_offline)
    else -> UiText(R.string.error_photo_remove_failed)
}

/** The item is gone from the server (deleted elsewhere), so the list wants a refresh. */
fun isItemGone(error: Throwable): Boolean = error is HttpException && error.code() == 404
