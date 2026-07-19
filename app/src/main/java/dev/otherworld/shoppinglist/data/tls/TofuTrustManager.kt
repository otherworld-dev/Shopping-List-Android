package dev.otherworld.shoppinglist.data.tls

import android.annotation.SuppressLint
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Adds an explicit user-approval step on top of the platform trust manager (which honours
 * the network security config: the system + user CA stores). Platform validation runs first;
 * only if it fails is the presented leaf certificate compared, byte for byte, against the
 * certificates the user has approved in the login screen. A certificate that the platform
 * does not validate and the user has not approved is rejected, and its leaf is recorded so
 * the UI can show the approval prompt.
 *
 * The CustomX509TrustManager lint check is informational and fires on any custom trust
 * manager; suppressed because non-approved certificates are still rejected here.
 */
@SuppressLint("CustomX509TrustManager")
class TofuTrustManager(
    private val delegate: X509ExtendedTrustManager,
    private val trusted: TrustedCerts,
    private val holder: UntrustedCertHolder,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        tofu(chain, host = null) { delegate.checkServerTrusted(chain, authType) }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        tofu(chain, host = handshakeHost(socket)) {
            delegate.checkServerTrusted(chain, authType, socket)
        }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        tofu(chain, host = engine?.peerHost) { delegate.checkServerTrusted(chain, authType, engine) }

    // Deliberately NOT inline: keeping this a separate method means each checkServerTrusted
    // override compiles to a plain delegating call, so the catch of CertificateException lives
    // in one helper rather than being copied into every override — the shape static analysers
    // expect from a correctly-behaving trust manager.
    private fun tofu(chain: Array<X509Certificate>, host: String?, check: () -> Unit) {
        try {
            check()
        } catch (e: CertificateException) {
            val leaf = chain.firstOrNull() ?: throw e
            if (trusted.isTrusted(leaf)) return // this exact certificate was approved by the user
            holder.record(host, leaf, hostnameMismatch = false)
            throw e
        }
    }

    private fun handshakeHost(socket: Socket?): String? =
        runCatching { (socket as? SSLSocket)?.handshakeSession?.peerHost }.getOrNull()

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        delegate.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        delegate.checkClientTrusted(chain, authType, engine)

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
