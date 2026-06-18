package dev.otherworld.shoppinglist.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.auth.LoginFlowV2Client
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val connecting: Boolean = false,
    val awaiting: Boolean = false,
    val launchUrl: String? = null,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginClient: LoginFlowV2Client,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun login(server: String) {
        if (server.isBlank() || _state.value.connecting) return
        _state.update { it.copy(connecting = true, error = null) }
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
                    // A network blip mid-poll shouldn't abort the flow — just try again next tick.
                    val account = runCatching { loginClient.poll(init.pollEndpoint, init.pollToken) }
                        .getOrNull()
                    if (account != null) {
                        credentialStore.save(account)
                        return@launch // RootNav observes the account and navigates away
                    }
                }
                _state.update {
                    it.copy(awaiting = false, error = "Timed out waiting for authorization.")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(connecting = false, awaiting = false, error = friendlyError(e))
                }
            }
        }
    }

    private suspend fun startWithRetry(server: String): LoginFlowV2Client.Init {
        var last: Exception? = null
        repeat(START_ATTEMPTS) { attempt ->
            try {
                return loginClient.start(server)
            } catch (e: java.io.IOException) {
                last = e
                if (attempt < START_ATTEMPTS - 1) delay(1_500)
            }
        }
        throw last ?: java.io.IOException("Could not reach the server")
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException ->
            "Couldn't reach the server. Check the address and your connection, then try again."
        else -> e.message ?: "Login failed"
    }

    fun onUrlLaunched() = _state.update { it.copy(launchUrl = null) }

    fun cancel() {
        pollJob?.cancel()
        _state.update { LoginUiState() }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60_000L
        const val START_ATTEMPTS = 4
    }
}
