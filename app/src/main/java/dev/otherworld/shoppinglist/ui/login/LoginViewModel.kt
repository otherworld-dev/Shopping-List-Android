package dev.otherworld.shoppinglist.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.auth.LoginFlowV2Client
import dev.otherworld.shoppinglist.data.tls.AcceptedCertStore
import dev.otherworld.shoppinglist.data.tls.CertInfo
import dev.otherworld.shoppinglist.data.tls.UntrustedCertHolder
import dev.otherworld.shoppinglist.data.tls.describeCert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.cert.X509Certificate
import javax.inject.Inject

data class LoginUiState(
    val connecting: Boolean = false,
    val awaiting: Boolean = false,
    val launchUrl: String? = null,
    val error: String? = null,
    val pendingCert: CertInfo? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginClient: LoginFlowV2Client,
    private val credentialStore: CredentialStore,
    private val acceptedCerts: AcceptedCertStore,
    private val certHolder: UntrustedCertHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var lastServer: String = ""
    private var pendingRaw: X509Certificate? = null

    fun login(server: String) {
        if (server.isBlank() || _state.value.connecting) return
        lastServer = server
        pendingRaw = null
        certHolder.consume() // drop any stale record from a previous attempt
        _state.update { it.copy(connecting = true, error = null, pendingCert = null) }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            try {
                // Retry the handshake through transient DNS/network blips (common on phones
                // with flaky "Private DNS").
                val init = startWithRetry(server)
                _state.update {
                    it.copy(connecting = false, awaiting = true, launchUrl = init.loginUrl)
                }
                val deadline = POLL_TIMEOUT_MS
                var elapsed = 0L
                while (elapsed < deadline) {
                    delay(POLL_INTERVAL_MS)
                    elapsed += POLL_INTERVAL_MS
                    // A network blip mid-poll shouldn't abort the flow — retry next tick. But a
                    // deterministic cert failure (e.g. the poll endpoint is a different host than
                    // the one the user pinned) must surface the accept prompt, not spin silently
                    // until the 5-minute timeout.
                    val account = try {
                        loginClient.poll(init.pollEndpoint, init.pollToken)
                    } catch (e: java.io.IOException) {
                        if (isNonRetryable(e)) throw e
                        null
                    }
                    if (account != null) {
                        credentialStore.save(account)
                        return@launch // RootNav observes the account and navigates away
                    }
                }
                _state.update {
                    it.copy(awaiting = false, error = "Timed out waiting for authorization.")
                }
            } catch (e: CancellationException) {
                // A newer login() cancelled this job — leave shared state and the cert holder
                // untouched so we don't overwrite the new attempt's UI or consume its record.
                throw e
            } catch (e: Exception) {
                val untrusted = certHolder.consume()
                if (untrusted != null && e is javax.net.ssl.SSLException) {
                    // The server presented a certificate the device doesn't trust (or one
                    // that doesn't cover this hostname): offer the accept prompt instead of
                    // a dead-end error. The record carries the host that actually presented
                    // the cert, so what we display and pin are always self-consistent.
                    pendingRaw = untrusted.certificate
                    val host = untrusted.host ?: hostOf(server)
                    _state.update {
                        it.copy(
                            connecting = false, awaiting = false, error = null,
                            pendingCert = describeCert(host, untrusted.certificate, untrusted.hostnameMismatch),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(connecting = false, awaiting = false, error = friendlyError(e))
                    }
                }
            }
        }
    }

    /** User tapped "Trust" in the prompt: pin the certificate for this host and retry. */
    fun trustPendingCert() {
        val cert = pendingRaw ?: return
        val host = _state.value.pendingCert?.host ?: return
        acceptedCerts.accept(host, cert)
        pendingRaw = null
        _state.update { it.copy(pendingCert = null) }
        login(lastServer)
    }

    fun dismissPendingCert() {
        pendingRaw = null
        _state.update { it.copy(pendingCert = null) }
    }

    private suspend fun startWithRetry(server: String): LoginFlowV2Client.Init {
        var last: Exception? = null
        repeat(START_ATTEMPTS) { attempt ->
            try {
                return loginClient.start(server)
            } catch (e: java.io.IOException) {
                // Certificate trust and hostname failures are deterministic — retrying only
                // delays the accept prompt / guidance. Mirrors OkHttp's own non-recoverable
                // set; transient handshake resets (no CertificateException cause) still retry.
                if (isNonRetryable(e)) throw e
                last = e
                if (attempt < START_ATTEMPTS - 1) delay(1_500)
            }
        }
        throw last ?: java.io.IOException("Could not reach the server")
    }

    private fun isNonRetryable(e: java.io.IOException): Boolean {
        if (e is java.net.UnknownServiceException) return true // cleartext http:// blocked
        return dev.otherworld.shoppinglist.data.tls.isCertFailure(e)
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException ->
            "Couldn't reach the server. Check the address and your connection, then try again."
        // These SSL branches are now fallbacks: a certificate failure normally surfaces the
        // accept prompt (above), so they fire only when no leaf was captured — a non-cert TLS
        // failure (protocol/cipher error, reset mid-handshake) or an empty chain.
        is javax.net.ssl.SSLPeerUnverifiedException ->
            "The server's certificate doesn't match the address you entered."
        is javax.net.ssl.SSLException ->
            "Couldn't establish a secure connection. Check the server's TLS configuration and " +
                "try again."
        else -> e.message ?: "Login failed"
    }

    private fun hostOf(server: String): String {
        val s = server.trim().trimEnd('/')
        val url = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        return url.toHttpUrlOrNull()?.host ?: s
    }

    fun onUrlLaunched() = _state.update { it.copy(launchUrl = null) }

    fun cancel() {
        pollJob?.cancel()
        pendingRaw = null
        _state.update { LoginUiState() }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60_000L
        const val START_ATTEMPTS = 4
    }
}
