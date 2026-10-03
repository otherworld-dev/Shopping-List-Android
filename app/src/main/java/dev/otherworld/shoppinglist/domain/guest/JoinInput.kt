package dev.otherworld.shoppinglist.domain.guest

import java.net.URI

/** What the Join a shared list screen was given. */
sealed interface JoinInput {
    /** A share link: straight to the Join screen. */
    data class Link(val link: ShareLink) : JoinInput

    /** An invite code and the server to ask about it. [code] is the 8 characters, no hyphen. */
    data class Code(val server: String, val code: String) : JoinInput

    /** A code on its own: the Server field has to say where it's from. */
    data object NeedsServer : JoinInput

    /** A code with a Server field that isn't a server address. */
    data object InvalidServer : JoinInput

    /** An http server: the app blocks cleartext, so it could never be reached. */
    data object Insecure : JoinInput

    data object Invalid : JoinInput
}

private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
private const val CODE_LENGTH = 8

/**
 * Drops every kind of space and turns the dashes U+2010 to U+2015 into plain ones. The server
 * only strips ASCII whitespace and "-", and a phone keyboard or a pasted message can produce the
 * look-alikes. Kotlin's isWhitespace covers U+00A0 and the other Unicode spaces.
 */
private fun clean(raw: String): String = buildString {
    for (c in raw) {
        when {
            c in '\u2010'..'\u2015' -> append('-')
            c.isWhitespace() -> Unit
            else -> append(c)
        }
    }
}

/**
 * An invite code as the server stores it (8 characters, upper case, no hyphen), or null when it
 * can't be one. Checked here so a typo never spends one of the server's throttled lookups.
 */
internal fun normaliseCode(raw: String): String? =
    clean(raw).replace("-", "").uppercase()
        .takeIf { it.length == CODE_LENGTH && it.all { c -> c in CODE_ALPHABET } }

private sealed interface ServerResult {
    data class Ok(val server: String) : ServerResult
    data object Insecure : ServerResult
    data object Invalid : ServerResult
}

/**
 * A server address as typed or taken from an invite, in the same form [parseShareLink] gives a
 * link's server (shares are matched on it): https, lower-case host, the port unless it's 443, any
 * sub-path, no index.php and no trailing slash. No scheme means https.
 */
private fun normaliseServer(raw: String): ServerResult {
    var text = clean(raw).trimEnd('/')
    if (text.isEmpty()) return ServerResult.Invalid
    val scheme = text.substringBefore("://", missingDelimiterValue = "")
    when {
        !text.contains("://") -> text = "https://$text"
        scheme.equals("http", ignoreCase = true) -> return ServerResult.Insecure
        !scheme.equals("https", ignoreCase = true) -> return ServerResult.Invalid
    }
    val uri = try {
        URI(text)
    } catch (_: Exception) {
        return ServerResult.Invalid
    }
    val host = uri.host?.lowercase() ?: return ServerResult.Invalid
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return ServerResult.Invalid
    val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/index.php").trimEnd('/')
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return ServerResult.Ok("https://$host$port$path")
}

private fun ServerResult.toInput(code: String): JoinInput = when (this) {
    is ServerResult.Ok -> JoinInput.Code(server, code)
    ServerResult.Insecure -> JoinInput.Insecure
    ServerResult.Invalid -> JoinInput.InvalidServer
}

/** One piece of input on its own: a link, an invite string, or a bare code. */
private fun parseOne(raw: String, server: String): JoinInput {
    val text = clean(raw)
    if (text.isEmpty()) return JoinInput.Invalid
    parseShareLink(text)?.let { return JoinInput.Link(it) }
    if (text.startsWith("http://", ignoreCase = true)) return JoinInput.Insecure
    val trimmed = text.trimEnd('/')
    val slash = trimmed.lastIndexOf('/')
    if (slash >= 0) {
        val code = normaliseCode(trimmed.substring(slash + 1)) ?: return JoinInput.Invalid
        return when (val result = normaliseServer(trimmed.substring(0, slash))) {
            is ServerResult.Ok -> JoinInput.Code(result.server, code)
            ServerResult.Insecure -> JoinInput.Insecure
            // The invite's own server is broken, so the whole thing isn't an invite.
            ServerResult.Invalid -> JoinInput.Invalid
        }
    }
    val code = normaliseCode(text) ?: return JoinInput.Invalid
    if (server.isBlank()) return JoinInput.NeedsServer
    return normaliseServer(server).toInput(code)
}

/**
 * Reads the Join a shared list screen: [input] is a share link, an invite string
 * (`cloud.example.com/K7QM-3XPD`, split at the last "/") or a bare code, and [server] is the
 * Server field, which only counts for a bare code. A whole pasted message that doesn't read as
 * one thing is tried word by word, so the link or invite inside it is still found.
 */
fun parseJoinInput(input: String, server: String = ""): JoinInput {
    val whole = parseOne(input, server)
    if (whole != JoinInput.Invalid) return whole
    return input.split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .takeIf { it.size > 1 }
        ?.map { parseOne(it, server) }
        ?.firstOrNull { it is JoinInput.Link || it is JoinInput.Code || it == JoinInput.Insecure }
        ?: JoinInput.Invalid
}

/** Whether [input] is a code on its own, which is when the screen shows its Server field. */
fun isBareCode(input: String): Boolean = parseJoinInput(input) == JoinInput.NeedsServer
