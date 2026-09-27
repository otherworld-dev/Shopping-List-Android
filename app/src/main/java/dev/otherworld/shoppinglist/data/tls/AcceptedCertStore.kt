package dev.otherworld.shoppinglist.data.tls

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the certificates the user has approved ("Trust" in the login prompt), one per
 * server, meaning host and port: two Nextclouds on one host can present different
 * certificates. Certificates are public data, so plain SharedPreferences is sufficient — the
 * same kind of app-private storage the official Nextcloud client uses for its known-servers
 * store. Approving a new certificate for a server replaces that server's entry, which is how
 * certificate rotation is handled; approving one for another port on the same host adds an
 * entry. Approvals saved before ports were recorded are keyed by host alone and still hold for
 * any port on that host (see [CertPins]). Entries are kept across logout: an approval describes
 * a server, not an account.
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

    override fun isTrustedForHost(host: String, port: Int, cert: X509Certificate): Boolean =
        CertPins.matches(pins, host, port, encode(cert))

    @Synchronized
    override fun accept(host: String, port: Int, cert: X509Certificate) {
        val encoded = encode(cert)
        pins = CertPins.put(pins, host, port, encoded)
        prefs.edit().putString(CertPins.key(host, port), encoded).apply()
    }

    private fun encode(cert: X509Certificate): String =
        Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
}

/**
 * The keys [AcceptedCertStore] saves approvals under: `"<host>|<port>"`, lowercase, or the host
 * alone when the port is unknown (-1). Entries saved before ports were recorded are also keyed
 * by the host alone, so a host-only entry matches any port on its host. `|` can't occur in a
 * hostname or an IPv6 address, so the host and port can't be misread.
 */
internal object CertPins {
    fun key(host: String, port: Int): String {
        val h = host.lowercase()
        return if (port == -1) h else "$h|$port"
    }

    /** True when [encoded] is the certificate approved for [host] on [port], or for [host] on any port. */
    fun matches(pins: Map<String, String>, host: String, port: Int, encoded: String): Boolean =
        pins[key(host, port)] == encoded || pins[host.lowercase()] == encoded

    /** [pins] with [encoded] approved for [host] on [port], replacing only that server's entry. */
    fun put(pins: Map<String, String>, host: String, port: Int, encoded: String): Map<String, String> =
        pins + (key(host, port) to encoded)
}
