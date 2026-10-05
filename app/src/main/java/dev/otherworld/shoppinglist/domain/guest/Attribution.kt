package dev.otherworld.shoppinglist.domain.guest

/** Who added and who ticked an item, as the server records it (server app 1.10.0 and later). */
data class Attributed(
    val addedBy: String?,
    val addedByName: String?,
    val addedByGuest: Boolean,
    val checkedBy: String?,
    val checkedByName: String?,
    val checkedByGuest: Boolean,
)

/** A name to show beside an item, and whether it's a guest's. */
data class Byline(val name: String, val guest: Boolean)

data class Attribution(val added: Byline?, val checked: Byline?)

private fun byline(userId: String?, name: String?, guest: Boolean, me: String?, showOwn: Boolean): Byline? {
    if (name.isNullOrEmpty()) return null
    if (guest) return Byline(name, guest = true)
    // Guest lists come from public responses, which carry no user ids, so this only matches when signed in
    if (me != null && userId == me && !showOwn) return null
    return Byline(name, guest = false)
}

/**
 * Who to name beside an item, ported from the web app's attribution.ts: account users plainly,
 * guests flagged (shown as a separate mark that a long name can't push out of view, so a guest
 * can't pass as a member), and yourself only if you asked to. [me] is null on a guest list.
 */
fun attribution(item: Attributed, me: String?, showOwn: Boolean) = Attribution(
    added = byline(item.addedBy, item.addedByName, item.addedByGuest, me, showOwn),
    checked = byline(item.checkedBy, item.checkedByName, item.checkedByGuest, me, showOwn),
)

/** The one name an item's row shows: who ticked it once it's ticked, otherwise who added it. */
fun bylineFor(item: Attributed, checked: Boolean, me: String?, showOwn: Boolean): Byline? =
    attribution(item, me, showOwn).let { if (checked) it.checked else it.added }
