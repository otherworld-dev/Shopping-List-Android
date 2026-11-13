package dev.otherworld.shoppinglist.data.tls

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Single place that wires the trust-on-first-use pieces, and the client certificate for a
 * server behind mutual TLS, into an OkHttpClient. Every client in the app (login, API,
 * realtime websocket) must go through [applyTo] so a user-accepted certificate works across
 * login, sync, and realtime alike.
 */
@Singleton
class TofuTls @Inject constructor(
    store: AcceptedCertStore,
    holder: UntrustedCertHolder,
    private val clientAliases: ClientCertStore,
    private val clientKeys: KeyChainKeySource,
    clientRequests: ClientCertRequests,
) {
    private val trustManager = TofuTrustManager(platformTrustManager(), store, holder)
    private val hostnameVerifier = TofuHostnameVerifier(store, holder)
    private val keyManager = ClientKeyManager(clientAliases, clientKeys, clientRequests)
    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(arrayOf(keyManager), arrayOf(trustManager), null)
    }
    private val socketFactory: SSLSocketFactory = sslContext.socketFactory

    // One pool for every client, so a newly picked client certificate can drop the connections
    // made without it: a server can accept the handshake with no certificate and answer each
    // request with an error, and a pooled connection would never offer the certificate.
    private val connectionPool = ConnectionPool()

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .sslSocketFactory(socketFactory, trustManager)
        .hostnameVerifier(hostnameVerifier)
        .connectionPool(connectionPool)

    /** Presents [alias] to [host] from now on, starting fresh connections so it is offered. */
    fun useClientCert(host: String, alias: String) {
        clientAliases.set(host, alias)
        clientKeys.forget(alias)
        // A resumed TLS session would skip the certificate request altogether
        val sessions = sslContext.clientSessionContext
        sessions.ids.toList().forEach { sessions.getSession(it)?.invalidate() }
        connectionPool.evictAll()
    }

    private companion object {
        /** The platform default trust manager — the same NSC-aware one OkHttp would use. */
        fun platformTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
        }
    }
}
