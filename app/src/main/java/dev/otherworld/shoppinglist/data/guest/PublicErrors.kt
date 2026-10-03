package dev.otherworld.shoppinglist.data.guest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** What a failed public API call means. */
sealed interface PublicError {
    data object PasswordRequired : PublicError
    data object WrongPassword : PublicError
    data object ReadOnly : PublicError
    data object NotFound : PublicError
    data class Other(val code: Int) : PublicError
}

/**
 * Reads the public API's error body: an OCS envelope whose data says why. Only the app's own
 * "Not found" is a missing link; a bare 404 (the app disabled, a proxy) is kept as its code.
 */
fun classifyPublicError(code: Int, body: String?, json: Json): PublicError {
    val data = body?.let {
        runCatching { json.parseToJsonElement(it).jsonObject["ocs"]?.jsonObject?.get("data") as? JsonObject }.getOrNull()
    }
    val message = (data?.get("message") as? JsonPrimitive)?.contentOrNull
    if (code == 404 && message == "Not found") return PublicError.NotFound
    if (code == 403 && data != null) {
        if ((data["passwordRequired"] as? JsonPrimitive)?.booleanOrNull == true) return PublicError.PasswordRequired
        when (message) {
            "Invalid password" -> return PublicError.WrongPassword
            "Read-only access" -> return PublicError.ReadOnly
        }
    }
    return PublicError.Other(code)
}

/** A link that needs a password the app doesn't have (none stored, or it was changed). */
class GuestPasswordNeededException(val shareId: Long) : RuntimeException()

/** The link was deleted or expired, or its list was removed. */
class GuestLinkDeadException(val shareId: Long) : RuntimeException()

/** The friend's server answered 404 without the app saying so (the app disabled, a proxy): try again later. */
class GuestServerTroubleException(val shareId: Long) : RuntimeException()

/** The owner made the link view only. */
class GuestReadOnlyException(val shareId: Long) : RuntimeException()

/** Opening a link that doesn't work (never did, or no longer does). */
class LinkNotFoundException : RuntimeException()

/** The password typed for a link was wrong. */
class WrongPasswordException : RuntimeException()

/** What a failed invite-code lookup means. */
sealed interface CodeError {
    /** The app's own "Not found": the code is wrong, expired, or its link was deleted. */
    data object NotFound : CodeError

    /** A 404 the app didn't send: Shopping List isn't on that server, or a proxy is in the way. */
    data object AppMissing : CodeError

    /** Rate limited, or the brute-force throttle has had enough. */
    data object TooManyTries : CodeError

    data class Other(val code: Int) : CodeError
}

fun classifyCodeError(code: Int, body: String?, json: Json): CodeError = when (code) {
    429 -> CodeError.TooManyTries
    404 -> if (classifyPublicError(code, body, json) == PublicError.NotFound) CodeError.NotFound else CodeError.AppMissing
    else -> CodeError.Other(code)
}

/** The server is older than invite codes (before 1.10.0). */
class CodesUnsupportedException : RuntimeException()

/** The code is wrong, expired, or its link was deleted. */
class CodeNotFoundException : RuntimeException()

/** The server answered, but not as Shopping List: the app isn't there, or a proxy is in the way. */
class ShoppingListMissingException : RuntimeException()

/** The server wants the phone to wait before trying again. */
class TooManyTriesException : RuntimeException()
