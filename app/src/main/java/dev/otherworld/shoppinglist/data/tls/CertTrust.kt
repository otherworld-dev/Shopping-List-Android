package dev.otherworld.shoppinglist.data.tls

import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/** Read side of the user-accepted certificate pins; faked in unit tests. */
interface TrustedCerts {
    /**
     * Exact match against a certificate the user accepted for [host], on any of its ports: the
     * port the TLS layer sees is the proxy's when there is one, so it can't be relied on.
     */
    fun isTrustedForHost(host: String, cert: X509Certificate): Boolean
}

/** Write side of the user-accepted certificate pins; faked in unit tests. */
interface CertApprover {
    /**
     * Records [cert] as approved for [host] on [port] (-1 when unknown), replacing any previous
     * approval for that same host and port.
     */
    fun accept(host: String, port: Int, cert: X509Certificate)
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
        /** The connection's port, or -1 when it isn't known. */
        val port: Int,
        val certificate: X509Certificate,
        val hostnameMismatch: Boolean,
    )

    // AtomicReference so two concurrently-failing handshakes can't both read the same record.
    private val pending = java.util.concurrent.atomic.AtomicReference<Untrusted?>(null)

    fun record(host: String?, port: Int, certificate: X509Certificate, hostnameMismatch: Boolean) {
        pending.set(Untrusted(host, port, certificate, hostnameMismatch))
    }

    /** Returns and clears the last rejected certificate, if any. */
    fun consume(): Untrusted? = pending.getAndSet(null)
}
