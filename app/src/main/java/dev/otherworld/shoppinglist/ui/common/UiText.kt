package dev.otherworld.shoppinglist.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.otherworld.shoppinglist.R
import retrofit2.HttpException
import java.io.IOException

/**
 * A message for the screen, held as a string resource so it's translated when shown. View models
 * use it instead of English text, which Crowdin could never reach.
 */
class UiText(@StringRes val id: Int, vararg args: Any) {
    val args: List<Any> = args.toList()

    fun asString(context: Context): String = context.getString(id, *args.toTypedArray())

    override fun equals(other: Any?) = other is UiText && other.id == id && other.args == args

    override fun hashCode() = 31 * id + args.hashCode()

    override fun toString() = "UiText($id, $args)"
}

@Composable
fun UiText.asString(): String = stringResource(id, *args.toTypedArray())

/**
 * What a failure means to the person using the app. An exception's own message is English and
 * often technical ("Unable to resolve host …"), so it never reaches the screen.
 */
fun errorText(error: Throwable): UiText = when {
    error is IOException -> UiText(R.string.error_offline)
    error is HttpException && error.code() == 403 -> UiText(R.string.error_no_permission)
    error is HttpException && (error.code() >= 500 || error.code() == 429) -> UiText(R.string.error_server)
    else -> UiText(R.string.error_generic)
}
