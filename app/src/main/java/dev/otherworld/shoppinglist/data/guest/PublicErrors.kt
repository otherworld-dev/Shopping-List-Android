package dev.otherworld.shoppinglist.data.guest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a failed public API call means. */
sealed interface PublicError {
    data object PasswordRequired : PublicError
    data object WrongPassword : PublicError
    data object ReadOnly : PublicError
    data object NotFound : PublicError
    data class Other(val code: Int) : PublicError
}

/** Reads the public API's error body: an OCS envelope whose data says why. */
fun classifyPublicError(code: Int, body: String?, json: Json): PublicError {
    if (code == 404) return PublicError.NotFound
    val data = body?.let {
        runCatching { json.parseToJsonElement(it).jsonObject["ocs"]?.jsonObject?.get("data") as? JsonObject }.getOrNull()
    }
    if (code == 403 && data != null) {
        if (data["passwordRequired"]?.jsonPrimitive?.booleanOrNull == true) return PublicError.PasswordRequired
        when (data["message"]?.jsonPrimitive?.contentOrNull) {
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

/** The owner made the link view only. */
class GuestReadOnlyException(val shareId: Long) : RuntimeException()

/** Opening a link that doesn't work (never did, or no longer does). */
class LinkNotFoundException : RuntimeException()

/** The password typed for a link was wrong. */
class WrongPasswordException : RuntimeException()
