package dev.otherworld.shoppinglist.data.tls

import okhttp3.internal.tls.OkHostnameVerifier
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * Standard OkHttp hostname verification (SAN-only, as everywhere on Android since API 28),
 * with one escape hatch: the certificate the user explicitly accepted for this exact host
 * passes even without a matching subjectAltName — self-signed certs very often lack one.
 * Failures are recorded so the login UI can offer the accept prompt.
 */
class TofuHostnameVerifier(
    private val trusted: TrustedCerts,
    private val holder: UntrustedCertHolder,
) : HostnameVerifier {

    override fun verify(hostname: String, session: SSLSession): Boolean {
        // OkHostnameVerifier lives in okhttp's internal package but is the engine behind
        // the default verifier; delegating keeps behaviour identical to stock OkHttp.
        if (OkHostnameVerifier.verify(hostname, session)) return true
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }
            .getOrNull() ?: return false
        if (trusted.isTrustedForHost(hostname, leaf)) return true
        holder.record(hostname, leaf, hostnameMismatch = true)
        return false
    }
}
