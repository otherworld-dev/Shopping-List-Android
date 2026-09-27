package dev.otherworld.shoppinglist.data.tls

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/** A server's lowercase host and its port (the scheme's default when its URL has none). */
data class ServerAddress(val host: String, val port: Int)

/** The host and port of the currently logged-in server, or null when logged out. */
fun interface ActiveServerHost {
    fun get(): ServerAddress?
}

/**
 * Surfaces the certificate-approval prompt when an authenticated request fails TLS after
 * login — e.g. a self-hosted server's certificate was replaced with one the device doesn't
 * trust. The failed leaf is already recorded by [UntrustedCertHolder]; this consumes it,
 * describes it for an app-level prompt, and — if the user approves — pins the new certificate
 * and signals a retry. Without this, such a change fails every sync silently until the user
 * happens to log out and back in.
 *
 * Records are accepted only for the currently logged-in host, and its port when the record's
 * port is known, so a request that fails its handshake just after logout, or one to a guest
 * list's server on another port of the same host, can't arm a prompt or pin against the wrong
 * server.
 */
@Singleton
class CertAlertController @Inject constructor(
    private val holder: UntrustedCertHolder,
    private val acceptedCerts: CertApprover,
    private val activeServerHost: ActiveServerHost,
) {
    private val _alert = MutableStateFlow<CertInfo?>(null)
    val alert: StateFlow<CertInfo?> = _alert.asStateFlow()

    // A dismissed-but-still-failing certificate. Kept so the app can show a persistent
    // "sync paused" banner instead of silently ceasing to sync.
    private val _suppressed = MutableStateFlow<CertInfo?>(null)
    val suppressed: StateFlow<CertInfo?> = _suppressed.asStateFlow()

    /** Emits after the user approves a certificate, so observers can drain sync and reopen push. */
    private val _retry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val retry: SharedFlow<Unit> = _retry.asSharedFlow()

    @Volatile private var pendingCert: X509Certificate? = null
    @Volatile private var pendingHost: String? = null
    @Volatile private var pendingPort: Int = -1
    // The certificate whose prompt the user dismissed: suppress re-prompting for that exact
    // certificate so a persistent failure doesn't nag on every sync tick. A different (newly
    // rotated) certificate still prompts.
    @Volatile private var dismissedCert: X509Certificate? = null

    /** Called from the network layer when an authenticated request fails certificate validation. */
    fun onTlsFailure() {
        if (_alert.value != null) return
        val current = activeServerHost.get() ?: return // logged out: ignore late in-flight failures
        val rec = holder.consume() ?: return
        val host = rec.host ?: return
        if (!isActiveServer(host, rec.port, current)) return // stale / different-server record
        if (rec.certificate == dismissedCert) return // banner already shown for this exact cert
        pendingCert = rec.certificate
        pendingHost = host
        pendingPort = rec.port
        _suppressed.value = null
        _alert.value = describeCert(host, rec.port, rec.certificate, rec.hostnameMismatch)
    }

    private fun isActiveServer(host: String, port: Int, active: ServerAddress?): Boolean =
        active != null && host.equals(active.host, ignoreCase = true) && (port == -1 || port == active.port)

    /** Re-open the prompt from the persistent "sync paused" banner. */
    fun review() {
        val info = _suppressed.value ?: return
        _suppressed.value = null
        _alert.value = info
    }

    fun trust() {
        val cert = pendingCert
        val host = pendingHost
        val port = pendingPort
        // Only pin if the failing server is still the active one (guards a logout/switch race).
        if (cert != null && host != null && isActiveServer(host, port, activeServerHost.get())) {
            acceptedCerts.accept(host, port, cert)
        }
        dismissedCert = null
        clearAll()
        _retry.tryEmit(Unit)
    }

    fun dismiss() {
        dismissedCert = pendingCert
        _suppressed.value = _alert.value // keep a persistent, tappable reminder
        _alert.value = null
    }

    /** Full reset on logout, including the banner and dismissal suppression. */
    fun onLoggedOut() {
        dismissedCert = null
        clearAll()
    }

    private fun clearAll() {
        pendingCert = null
        pendingHost = null
        pendingPort = -1
        _alert.value = null
        _suppressed.value = null
    }
}
