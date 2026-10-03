package dev.otherworld.shoppinglist.domain.guest

/** A link's invite code as people see it, `K7QM-3XPD`, or null when there's no usable code. */
fun formatInviteCode(code: String?): String? =
    code?.let(::normaliseCode)?.let { "${it.take(4)}-${it.drop(4)}" }

/**
 * What Copy invite puts on the clipboard: the server without "https://" (port and sub-path kept),
 * a "/", then the code, the same text the web app's Copy invite button gives.
 */
fun inviteText(server: String, code: String?): String? {
    val formatted = formatInviteCode(code) ?: return null
    val address = server.trim().trimEnd('/').let {
        if (it.startsWith("https://", ignoreCase = true)) it.substring("https://".length) else it
    }
    if (address.isEmpty()) return null
    return "$address/$formatted"
}
