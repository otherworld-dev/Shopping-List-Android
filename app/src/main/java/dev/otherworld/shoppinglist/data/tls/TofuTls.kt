package dev.otherworld.shoppinglist.data.tls

import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Single place that wires the trust-on-first-use pieces into an OkHttpClient. Every client
 * in the app (login, API, realtime websocket) must go through [applyTo] so a user-accepted
 * certificate works across login, sync, and realtime alike.
 */
@Singleton
class TofuTls @Inject constructor(
    store: AcceptedCertStore,
    holder: UntrustedCertHolder,
) {
    private val trustManager = TofuTrustManager(platformTrustManager(), store, holder)
    private val hostnameVerifier = TofuHostnameVerifier(store, holder)
    private val socketFactory: SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), null)
    }.socketFactory

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .sslSocketFactory(socketFactory, trustManager)
        .hostnameVerifier(hostnameVerifier)

    private companion object {
        /** The platform default trust manager — the same NSC-aware one OkHttp would use. */
        fun platformTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
        }
    }
}
