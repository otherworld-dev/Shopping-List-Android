package dev.otherworld.shoppinglist.data.tls

import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/** Read side of the user-accepted certificate pins; faked in unit tests. */
interface TrustedCerts {
    /** Exact (byte-equal) match against any user-accepted certificate. */
    fun isTrusted(cert: X509Certificate): Boolean

    /** Exact match against the certificate the user accepted for [host] specifically. */
    fun isTrustedForHost(host: String, cert: X509Certificate): Boolean
}

/** Write side of the user-accepted certificate pins; faked in unit tests. */
interface CertApprover {
    /** Records [cert] as approved for [host], replacing any previous approval for that host. */
    fun accept(host: String, cert: X509Certificate)
}

/**
 * Passes the certificate that failed validation from the TLS stack to the login UI.
 * Recording a certificate here has no effect on trust — the handshake still fails; the UI
 * uses the recorded leaf only to populate the "Trust this server?" prompt.
 */
@Singleton
class UntrustedCertHolder @Inject constructor() {
    data class Untrusted(
        val host: String?,
        val certificate: X509Certificate,
        val hostnameMismatch: Boolean,
    )

    // AtomicReference so two concurrently-failing handshakes can't both read the same record.
    private val pending = java.util.concurrent.atomic.AtomicReference<Untrusted?>(null)

    fun record(host: String?, certificate: X509Certificate, hostnameMismatch: Boolean) {
        pending.set(Untrusted(host, certificate, hostnameMismatch))
    }

    /** Returns and clears the last rejected certificate, if any. */
    fun consume(): Untrusted? = pending.getAndSet(null)
}
