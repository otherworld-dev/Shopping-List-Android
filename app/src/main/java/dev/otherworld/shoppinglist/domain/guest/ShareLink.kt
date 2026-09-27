package dev.otherworld.shoppinglist.domain.guest

import java.net.URI

/** A public share link, split into the Nextcloud it lives on (with any sub-path) and its token. */
data class ShareLink(val server: String, val token: String) {
    /** The link as the web app hands it out. */
    val url: String get() = "$server$SHARE_PATH$token"
}

private const val SHARE_PATH = "/apps/shopping_list/s/"

/**
 * Reads a link to the web app's public list page:
 * `https://host[:port][/sub-path][/index.php]/apps/shopping_list/s/{token}`, with or without a
 * trailing slash, query or fragment. Only https: the app blocks cleartext, so an http link could
 * never be opened.
 */
fun parseShareLink(raw: String): ShareLink? {
    val uri = try {
        URI(raw.trim())
    } catch (_: Exception) {
        return null
    }
    if (!"https".equals(uri.scheme, ignoreCase = true)) return null
    val host = uri.host?.lowercase() ?: return null
    val path = uri.path ?: return null
    val at = path.indexOf(SHARE_PATH)
    if (at < 0) return null
    val token = path.substring(at + SHARE_PATH.length).removeSuffix("/")
    if (token.isEmpty() || '/' in token) return null
    val prefix = path.substring(0, at).removeSuffix("/index.php").trimEnd('/')
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return ShareLink("https://$host$port$prefix", token)
}
