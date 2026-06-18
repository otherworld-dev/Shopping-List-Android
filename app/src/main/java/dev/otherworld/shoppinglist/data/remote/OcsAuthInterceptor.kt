package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.auth.CredentialStore
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Rewrites the placeholder host of every OCS request to the currently connected server
 * (supporting subpath installs) and attaches HTTP Basic auth plus the headers Nextcloud
 * requires for OCS API access.
 */
class OcsAuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val account = credentialStore.current() ?: return chain.proceed(request)
        val server = account.server.toHttpUrlOrNull() ?: return chain.proceed(request)

        val prefix = server.encodedPath.trim('/')
        val original = request.url.encodedPath.trim('/')
        val mergedPath = if (prefix.isEmpty()) original else "$prefix/$original"

        val newUrl = request.url.newBuilder()
            .scheme(server.scheme)
            .host(server.host)
            .port(server.port)
            .encodedPath("/$mergedPath")
            .build()

        val authed = request.newBuilder()
            .url(newUrl)
            .header("Authorization", Credentials.basic(account.loginName, account.appPassword))
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(authed)
    }
}
