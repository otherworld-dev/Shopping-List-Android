package dev.otherworld.shoppinglist.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.guest.CodeNotFoundException
import dev.otherworld.shoppinglist.data.guest.CodesUnsupportedException
import dev.otherworld.shoppinglist.data.guest.InviteCodes
import dev.otherworld.shoppinglist.data.guest.ShoppingListMissingException
import dev.otherworld.shoppinglist.data.guest.TooManyTriesException
import dev.otherworld.shoppinglist.data.tls.CertApprover
import dev.otherworld.shoppinglist.data.tls.CertInfo
import dev.otherworld.shoppinglist.data.tls.UntrustedCertHolder
import dev.otherworld.shoppinglist.data.tls.describeCert
import dev.otherworld.shoppinglist.domain.guest.JoinInput
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import dev.otherworld.shoppinglist.domain.guest.parseJoinInput
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.cert.X509Certificate
import javax.inject.Inject

data class JoinEntryUiState(
    val busy: Boolean = false,
    val error: UiText? = null,
    val pendingCert: CertInfo? = null,
    /** The share link to hand to the Join screen, until the screen has gone there. */
    val link: ShareLink? = null,
)

/** What a failed code lookup says to the person using the app. */
internal fun joinEntryErrorText(e: Throwable): UiText = when (e) {
    is CodesUnsupportedException -> UiText(R.string.join_entry_codes_unsupported)
    is CodeNotFoundException -> UiText(R.string.join_entry_code_not_found)
    is ShoppingListMissingException -> UiText(R.string.join_entry_app_missing)
    is TooManyTriesException -> UiText(R.string.join_too_many_tries)
    else -> errorText(e)
}

/** Join a shared list: turns a pasted link, an invite or a code into a share link for Join. */
@HiltViewModel
class JoinEntryViewModel @Inject constructor(
    private val codes: InviteCodes,
    private val acceptedCerts: CertApprover,
    private val certHolder: UntrustedCertHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinEntryUiState())
    val state: StateFlow<JoinEntryUiState> = _state.asStateFlow()
    private var lastAttempt: JoinInput.Code? = null
    private var pendingRaw: X509Certificate? = null

    fun submit(input: String, server: String) {
        if (_state.value.busy) return
        when (val parsed = parseJoinInput(input, server)) {
            is JoinInput.Link -> _state.update { it.copy(error = null, link = parsed.link) }
            is JoinInput.Code -> resolve(parsed)
            JoinInput.NeedsServer -> showError(R.string.join_entry_server_needed)
            JoinInput.InvalidServer -> showError(R.string.join_entry_server_invalid)
            JoinInput.Insecure -> showError(R.string.join_entry_insecure)
            JoinInput.Invalid -> showError(R.string.join_entry_invalid)
        }
    }

    /** The screen has gone to Join with the link. */
    fun onLinkHandled() {
        _state.update { it.copy(link = null) }
    }

    private fun showError(id: Int) {
        _state.update { it.copy(error = UiText(id)) }
    }

    private fun resolve(attempt: JoinInput.Code) {
        lastAttempt = attempt
        // A background guest refresh against another server can leave a stale record in this
        // (singleton) holder; drop it so a failure here isn't blamed on someone else's certificate.
        certHolder.consume()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val link = codes.resolve(attempt.server, attempt.code)
                _state.update { it.copy(busy = false, link = link) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, attempt.server)
            }
        }
    }

    private fun fail(e: Exception, server: String) {
        val untrusted = certHolder.consume()
        val url = server.toHttpUrlOrNull()
        val host = url?.host.orEmpty()
        // Only this server's certificate counts; the port isn't compared, since behind a proxy the
        // recorded port is the proxy's.
        if (e is javax.net.ssl.SSLException && untrusted != null && untrusted.host.equals(host, ignoreCase = true)) {
            pendingRaw = untrusted.certificate
            val port = if (untrusted.port == -1) url?.port ?: 443 else untrusted.port
            _state.update {
                it.copy(busy = false, pendingCert = describeCert(host, port, untrusted.certificate, untrusted.hostnameMismatch))
            }
            return
        }
        _state.update { it.copy(busy = false, error = joinEntryErrorText(e)) }
    }

    fun trustPendingCert() {
        val cert = pendingRaw ?: return
        val pending = _state.value.pendingCert ?: return
        acceptedCerts.accept(pending.host, pending.port, cert)
        pendingRaw = null
        _state.update { it.copy(pendingCert = null) }
        lastAttempt?.let(::resolve)
    }

    fun dismissPendingCert() {
        pendingRaw = null
        _state.update { it.copy(pendingCert = null, error = UiText(R.string.login_error_tls)) }
    }
}
