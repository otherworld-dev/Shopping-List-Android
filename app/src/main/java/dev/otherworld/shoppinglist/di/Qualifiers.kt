package dev.otherworld.shoppinglist.di

import javax.inject.Qualifier

/** OkHttpClient used for the unauthenticated Login Flow v2 handshake. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LoginClient

/** OkHttpClient used for authenticated OCS API calls (adds Basic auth + OCS headers). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

/** OkHttpClient for share links: no login, its own in-memory cookies for password unlocks. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GuestClient
