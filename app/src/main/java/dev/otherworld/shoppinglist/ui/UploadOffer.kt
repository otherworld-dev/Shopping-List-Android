package dev.otherworld.shoppinglist.ui

/** Lists on this phone that could go to the account just logged in to, on [server]. */
data class UploadOffer(val count: Int, val server: String)

/**
 * Whether to offer uploading the phone's lists: only on a login during this session (not an
 * account already there at start-up, where [wasLoggedIn] is null) and only with lists to send.
 */
fun offersUpload(wasLoggedIn: Boolean?, loggedIn: Boolean, localLists: Int): Boolean =
    wasLoggedIn == false && loggedIn && localLists > 0
