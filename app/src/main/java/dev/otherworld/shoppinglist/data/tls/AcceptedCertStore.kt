package dev.otherworld.shoppinglist.data.tls

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the certificates the user has approved ("Trust" in the login prompt), kept per
 * server, meaning host and port, so approving one server's certificate never replaces
 * another's: two Nextclouds on one host can present different certificates. Approving a new
 * certificate for the same server replaces that server's entry, which is how certificate
 * rotation is handled. A certificate is trusted on any port of a host it was approved for,
 * because behind a proxy the TLS layer sees the proxy's port rather than the server's.
 * Approvals saved before ports were recorded are keyed by host alone (see [CertPins]).
 *
 * Certificates are public data, so plain SharedPreferences is sufficient — the same kind of
 * app-private storage the official Nextcloud client uses for its known-servers store. Entries
 * are kept across logout: an approval describes a server, not an account.
 */
@Singleton
class AcceptedCertStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrustedCerts, CertApprover {

    private val prefs = context.getSharedPreferences("accepted_certs", Context.MODE_PRIVATE)

    /**
     * [CertPins] key -> Base64 DER. Immutable snapshot replaced on write, so TLS handshake
     * threads can read without locking.
     */
    @Volatile private var pins: Map<String, String> =
        prefs.all.entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    override fun isTrustedForHost(host: String, cert: X509Certificate): Boolean =
        CertPins.matches(pins, host, encode(cert))

    @Synchronized
    override fun accept(host: String, port: Int, cert: X509Certificate) {
        val key = CertPins.key(host, port)
        val encoded = encode(cert)
        pins = pins + (key to encoded)
        prefs.edit().putString(key, encoded).apply()
    }

    private fun encode(cert: X509Certificate): String =
        Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
}

/**
 * The keys [AcceptedCertStore] saves approvals under, `"<host>|<port>"` lowercase, or the host
 * alone when the port is unknown (-1) or the entry predates ports, and how they're matched.
 * `|` can't occur in a hostname or an IPv6 address, so the host and port can't be misread.
 */
internal object CertPins {
    fun key(host: String, port: Int): String {
        val h = host.lowercase()
        return if (port == -1) h else "$h|$port"
    }

    /** True when [encoded] is a certificate approved for [host], on any port or none. */
    fun matches(pins: Map<String, String>, host: String, encoded: String): Boolean {
        val h = host.lowercase()
        val withPort = "$h|"
        return pins.any { (key, value) -> value == encoded && (key == h || key.startsWith(withPort)) }
    }
}
