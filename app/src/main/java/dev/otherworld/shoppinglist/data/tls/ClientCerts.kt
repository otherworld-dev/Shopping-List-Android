package dev.otherworld.shoppinglist.data.tls

import android.content.Context
import android.security.KeyChain
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Client certificates for servers behind mutual TLS (#35). The certificate and its key stay in
 * Android's KeyChain, where the user installed them; the app keeps only the alias picked for
 * each host and asks KeyChain for the key during the handshake, as the official Nextcloud app
 * does.
 */
interface ClientCertAliases {
    /** The alias picked for [host], on any port: behind a proxy the TLS layer sees the proxy's. */
    fun aliasFor(host: String): String?

    fun set(host: String, alias: String)
}

/** Where a certificate's chain and key come from: KeyChain in the app, a fake in unit tests. */
interface ClientKeySource {
    fun chain(alias: String): Array<X509Certificate>?

    fun key(alias: String): PrivateKey?
}

@Singleton
class ClientCertStore @Inject constructor(
    @ApplicationContext context: Context,
) : ClientCertAliases {

    private val prefs = context.getSharedPreferences("client_certs", Context.MODE_PRIVATE)

    // host -> alias. Immutable snapshot replaced on write, so handshake threads read without locking.
    @Volatile private var aliases: Map<String, String> =
        prefs.all.entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    override fun aliasFor(host: String): String? = aliases[host.lowercase()]

    @Synchronized
    override fun set(host: String, alias: String) {
        val key = host.lowercase()
        aliases = aliases + (key to alias)
        prefs.edit().putString(key, alias).apply()
    }
}

/**
 * Passes "this server asked for a certificate" from the TLS stack to the login screen, which
 * opens the system certificate picker when the sign-in then fails. Like [UntrustedCertHolder],
 * recording has no effect on the handshake itself.
 */
@Singleton
class ClientCertRequests @Inject constructor() {
    data class Request(
        val host: String,
        /** The connection's port, or -1 when it isn't known. */
        val port: Int,
        /** The alias the app presented, or null when it had none for this host. */
        val sent: String?,
    )

    private val pending = AtomicReference<Request?>(null)

    fun record(host: String, port: Int, sent: String?) = pending.set(Request(host, port, sent))

    /** Returns and clears the last request, if any. */
    fun consume(): Request? = pending.getAndSet(null)
}

/**
 * Answers a server's certificate request with the certificate picked for its host, or with
 * none. Either way the request is recorded, so a failed sign-in can offer the picker.
 */
class ClientKeyManager(
    private val aliases: ClientCertAliases,
    private val keys: ClientKeySource,
    private val requests: ClientCertRequests,
) : X509ExtendedKeyManager() {

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? {
        val session = (socket as? SSLSocket)?.handshakeSession
        val host = session?.peerHost ?: socket?.inetAddress?.hostName
        val port = session?.peerPort?.takeIf { it > 0 } ?: socket?.port ?: -1
        return pick(host, port)
    }

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        pick(engine?.peerHost, engine?.peerPort ?: -1)

    internal fun pick(host: String?, port: Int): String? {
        if (host == null) return null
        // A certificate since removed from the phone counts as none, so the picker comes up again
        val alias = aliases.aliasFor(host)?.takeIf { keys.key(it) != null }
        requests.record(host.lowercase(), port, alias)
        return alias
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = alias?.let(keys::chain)

    override fun getPrivateKey(alias: String?): PrivateKey? = alias?.let(keys::key)

    // Client side only
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}

/**
 * Reads certificates from KeyChain. Each read is a call into a system service, so results are
 * kept for the life of the process; a picked alias is read again in [forget].
 */
@Singleton
class KeyChainKeySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClientKeySource {

    private val chains = ConcurrentHashMap<String, Array<X509Certificate>>()
    private val keys = ConcurrentHashMap<String, PrivateKey>()

    override fun chain(alias: String): Array<X509Certificate>? =
        chains[alias] ?: read { KeyChain.getCertificateChain(context, alias) }?.also { chains[alias] = it }

    override fun key(alias: String): PrivateKey? =
        keys[alias] ?: read { KeyChain.getPrivateKey(context, alias) }?.also { keys[alias] = it }

    fun forget(alias: String) {
        chains.remove(alias)
        keys.remove(alias)
    }

    // Never called on the main thread: only from TLS handshakes, which run on OkHttp's threads
    private fun <T> read(block: () -> T?): T? = try {
        block()
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: Exception) {
        // No grant for this alias any more, or the certificate was removed
        Log.w("ClientCerts", "KeyChain read failed: ${e.javaClass.simpleName}")
        null
    }
}
