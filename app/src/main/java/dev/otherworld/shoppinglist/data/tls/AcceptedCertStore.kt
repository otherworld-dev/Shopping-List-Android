package dev.otherworld.shoppinglist.data.tls

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the certificates the user has approved ("Trust" in the login prompt), one per
 * host. Certificates are public data, so plain SharedPreferences is sufficient — the same
 * kind of app-private storage the official Nextcloud client uses for its known-servers
 * store. Approving a new certificate for a host replaces the previous entry, which is how
 * certificate rotation is handled. Entries are kept across logout: an approval describes a
 * server, not an account.
 */
@Singleton
class AcceptedCertStore @Inject constructor(
    @ApplicationContext context: Context,
) : TrustedCerts, CertApprover {

    private val prefs = context.getSharedPreferences("accepted_certs", Context.MODE_PRIVATE)

    /**
     * host (lowercase) -> Base64 DER. Immutable snapshot replaced on write, so TLS handshake
     * threads can read without locking.
     */
    @Volatile private var byHost: Map<String, String> =
        prefs.all.entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    override fun isTrusted(cert: X509Certificate): Boolean {
        val encoded = encode(cert)
        return byHost.values.any { it == encoded }
    }

    override fun isTrustedForHost(host: String, cert: X509Certificate): Boolean =
        byHost[host.lowercase()] == encode(cert)

    @Synchronized
    override fun accept(host: String, cert: X509Certificate) {
        val key = host.lowercase()
        val encoded = encode(cert)
        byHost = byHost + (key to encoded)
        prefs.edit().putString(key, encoded).apply()
    }

    private fun encode(cert: X509Certificate): String =
        Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
}
