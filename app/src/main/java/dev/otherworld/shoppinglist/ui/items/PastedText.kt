package dev.otherworld.shoppinglist.ui.items

/**
 * What a paste added to the add box, from the text [before] and [after] it: whatever lies
 * between the two texts' common start and common end. The box doubles as a search, so a list
 * pasted after typing "mil" must add "eggs", not "mileggs" — the web app's paste handler only
 * ever reads the clipboard.
 */
internal fun pastedText(before: String, after: String): String {
    val prefix = before.commonPrefixWith(after).length
    val maxSuffix = minOf(before.length, after.length) - prefix
    val suffix = before.commonSuffixWith(after).length.coerceAtMost(maxSuffix)
    return after.substring(prefix, after.length - suffix)
}
