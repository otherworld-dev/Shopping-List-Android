package dev.otherworld.shoppinglist.ui.join

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.guest.GuestPasswordNeededException
import dev.otherworld.shoppinglist.data.guest.GuestRepository
import dev.otherworld.shoppinglist.data.guest.LinkNotFoundException
import dev.otherworld.shoppinglist.data.guest.LinkPreview
import dev.otherworld.shoppinglist.data.guest.WrongPasswordException
import dev.otherworld.shoppinglist.data.tls.AcceptedCertStore
import dev.otherworld.shoppinglist.data.tls.CertInfo
import dev.otherworld.shoppinglist.data.tls.UntrustedCertHolder
import dev.otherworld.shoppinglist.data.tls.describeCert
import dev.otherworld.shoppinglist.domain.guest.parseShareLink
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.ui.common.UiText
import dev.otherworld.shoppinglist.ui.common.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.security.cert.X509Certificate
import javax.inject.Inject

data class OpenedList(val listId: Long, val title: String, val canWrite: Boolean)

data class JoinUiState(
    val checking: Boolean = true,
    val host: String = "",
    val preview: LinkPreview? = null,
    val error: UiText? = null,
    val passwordWrong: Boolean = false,
    val opening: Boolean = false,
    val opened: OpenedList? = null,
    val pendingCert: CertInfo? = null,
)

@HiltViewModel
class JoinViewModel @Inject constructor(
    private val guests: GuestRepository,
    private val acceptedCerts: AcceptedCertStore,
    private val certHolder: UntrustedCertHolder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val link = parseShareLink(savedStateHandle.get<String>("url").orEmpty())
    private val _state = MutableStateFlow(JoinUiState(host = link?.server?.toHttpUrlOrNull()?.host.orEmpty()))
    val state: StateFlow<JoinUiState> = _state.asStateFlow()
    private var pendingRaw: X509Certificate? = null

    init {
        checkLink()
    }

    fun checkLink() {
        val link = link ?: run {
            _state.update { it.copy(checking = false, error = UiText(R.string.join_link_invalid)) }
            return
        }
        certHolder.consume()
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val preview = guests.preview(link)
                _state.update { it.copy(checking = false, preview = preview) }
                // A link already opened here, and not waiting for a password, just opens.
                if (preview.joined && !preview.passwordRequired) open(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GuestPasswordNeededException) {
                // Not a failure: the link needs a password, same as the screen's normal state.
                needsPassword()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun open(password: String?) {
        val link = link ?: return
        if (_state.value.opening) return
        // Same as checkLink(): a background guest refresh against another server can leave a
        // stale record in this (singleton) holder; drop it before this attempt runs so a real
        // failure here isn't confused with one that belongs to a different host.
        certHolder.consume()
        _state.update { it.copy(opening = true, passwordWrong = false, error = null) }
        viewModelScope.launch {
            try {
                val listId = guests.join(link, password?.ifBlank { null })
                val preview = _state.value.preview
                val title = preview?.title?.takeIf { it.isNotBlank() } ?: link.server
                _state.update {
                    it.copy(opening = false, opened = OpenedList(listId, title, (preview?.permission ?: Permission.READ) >= Permission.WRITE))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPasswordException) {
                _state.update { it.copy(opening = false, passwordWrong = true) }
            } catch (e: GuestPasswordNeededException) {
                // Same as above: the stored unlock no longer works, so ask for a password rather
                // than reporting an error.
                needsPassword()
            } catch (e: HttpException) {
                if (e.code() == 403) {
                    needsPassword()
                } else {
                    _state.update { it.copy(opening = false) }
                    fail(e)
                }
            } catch (e: Exception) {
                _state.update { it.copy(opening = false) }
                fail(e)
            }
        }
    }

    /** Shows the password box, keeping any title already known from a previous preview. */
    private fun needsPassword() {
        _state.update {
            val existing = it.preview ?: LinkPreview(title = "", permission = Permission.READ, passwordRequired = false, joined = true)
            it.copy(checking = false, opening = false, preview = existing.copy(passwordRequired = true))
        }
    }

    private fun fail(e: Exception) {
        val untrusted = certHolder.consume()
        val host = _state.value.host
        // The holder is a singleton shared with background guest refreshes against other
        // servers, so only trust it as this link's failure when the host actually matches —
        // otherwise it's someone else's rejected certificate and this is a normal error.
        if (e is javax.net.ssl.SSLException && untrusted != null && untrusted.host.equals(host, ignoreCase = true)) {
            pendingRaw = untrusted.certificate
            _state.update {
                it.copy(checking = false, pendingCert = describeCert(host, untrusted.certificate, untrusted.hostnameMismatch))
            }
            return
        }
        val message = if (e is LinkNotFoundException) UiText(R.string.guest_link_dead) else errorText(e)
        _state.update { it.copy(checking = false, error = message) }
    }

    fun trustPendingCert() {
        val cert = pendingRaw ?: return
        val host = _state.value.pendingCert?.host ?: return
        acceptedCerts.accept(host, cert)
        pendingRaw = null
        _state.update { it.copy(pendingCert = null) }
        checkLink()
    }

    fun dismissPendingCert() {
        pendingRaw = null
        _state.update { it.copy(pendingCert = null, error = UiText(R.string.login_error_tls)) }
    }
}
