package dev.otherworld.shoppinglist.data.auth

/**
 * A connected Nextcloud account. [appPassword] is an app-specific password issued by
 * Login Flow v2 (never the user's real password) and is used for HTTP Basic auth.
 */
data class Account(
    val server: String,
    val loginName: String,
    val appPassword: String,
)
