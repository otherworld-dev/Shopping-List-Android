package dev.otherworld.shoppinglist.domain.guest

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    val server = "https://$host$port$path"
    // URI is lenient (a port of 99999, say); the request itself goes through OkHttp's stricter
    // HttpUrl, so anything it would refuse isn't a server address either.
    if (server.toHttpUrlOrNull() == null) return ServerResult.Invalid
    return ServerResult.Ok(server)
}

private fun ServerResult.toInput(code: String): JoinInput = when (this) {
    is ServerResult.Ok -> JoinInput.Code(server, code)
    ServerResult.Insecure -> JoinInput.Insecure
    ServerResult.Invalid -> JoinInput.InvalidServer
}

/** A link or an invite string in one piece of text; anything else is [JoinInput.Invalid]. */
private fun parsePiece(raw: String): JoinInput {
    val text = clean(raw)
    if (text.isEmpty()) return JoinInput.Invalid
    parseShareLink(text)?.let { return JoinInput.Link(it) }
    if (text.startsWith("http://", ignoreCase = true)) {
        // Only an http link or invite is worth a message of its own; any other http address
        // (the app's web page, say) is just another word.
        val asHttps = parsePiece("https://" + text.substring("http://".length))
        return if (asHttps is JoinInput.Link || asHttps is JoinInput.Code) JoinInput.Insecure else JoinInput.Invalid
    }
    val trimmed = text.trimEnd('/')
    val slash = trimmed.lastIndexOf('/')
    if (slash < 0) return JoinInput.Invalid
    val code = normaliseCode(trimmed.substring(slash + 1)) ?: return JoinInput.Invalid
    return when (val result = normaliseServer(trimmed.substring(0, slash))) {
        is ServerResult.Ok -> JoinInput.Code(result.server, code)
        ServerResult.Insecure -> JoinInput.Insecure
        // The invite's own server is broken, so the whole thing isn't an invite.
        ServerResult.Invalid -> JoinInput.Invalid
    }
}

/** Brackets, quotes and sentence punctuation a link or invite can sit inside in a message. */
private const val LEADING_PUNCTUATION = "(<\"'“‘«"
private const val TRAILING_PUNCTUATION = ".,;:!?)>\"'”’»"

private fun trimPunctuation(text: String): String =
    text.trimStart { it in LEADING_PUNCTUATION }.trimEnd { it in TRAILING_PUNCTUATION }

/**
 * Reads the Join a shared list screen: [input] is a share link, an invite string
 * (`cloud.example.com/K7QM-3XPD`, split at the last "/") or a bare code, and [server] is the
 * Server field, which only counts for a bare code.
 *
 * A pasted message is read word by word, never with its spaces deleted, which would glue a link
 * to the next word or a word onto an invite's server. Two or three neighbouring words are also
 * tried together, for a code typed in pieces ("K7QM 3XPD", "K7QM - 3XPD"), but only after every
 * single word, and a link or invite anywhere beats an http one.
 */
fun parseJoinInput(input: String, server: String = ""): JoinInput {
    // isWhitespace, not the regex \s: on the JVM that's ASCII only, and misses U+00A0.
    val words = String(CharArray(input.length) { if (input[it].isWhitespace()) ' ' else input[it] })
        .split(' ').map(::trimPunctuation).filter { it.isNotEmpty() }
    val found = (1..3).asSequence()
        .flatMap { n -> words.windowed(n) { it.joinToString("") } }
        .map(::parsePiece)
        .toList()
    found.firstOrNull { it is JoinInput.Link || it is JoinInput.Code }?.let { return it }
    if (JoinInput.Insecure in found) return JoinInput.Insecure
    val code = findBareCode(input, words) ?: return JoinInput.Invalid
    if (server.isBlank()) return JoinInput.NeedsServer
    return normaliseServer(server).toInput(code)
}

/** A code as the web app shows it, `K7QM-3XPD`, before it's normalised. */
private val SHOWN_CODE = Regex("[0-9A-Za-z]{4}-[0-9A-Za-z]{4}")

/**
 * The one code in [input]: all of it (a code typed in pieces), or else the single word, or run of
 * two or three words, that reads as one. When several do, only one shown as `XXXX-XXXX` counts,
 * since an ordinary word can be made of code characters ("REMEMBER"); still more than one is no
 * code at all, rather than a guess.
 */
private fun findBareCode(input: String, words: List<String>): String? {
    normaliseCode(trimPunctuation(input.trim()))?.let { return it }
    for (n in 1..3) {
        val codes = words.windowed(n) { it.joinToString("") }
            .mapNotNull { piece -> normaliseCode(piece)?.let { piece to it } }
            .distinctBy { it.second }
        when {
            codes.size == 1 -> return codes.single().second
            codes.size > 1 -> return codes.filter { SHOWN_CODE.matches(clean(it.first)) }.singleOrNull()?.second
        }
    }
    return null
}

/** Whether [input] is a code without a server, which is when the screen shows its Server field. */
fun isBareCode(input: String): Boolean = parseJoinInput(input) == JoinInput.NeedsServer
