package dev.otherworld.shoppinglist.data.sync

import android.util.Log
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.remote.OcsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time updates via Nextcloud's notify_push WebSocket. Feature-detected from
 * /cloud/capabilities; if the server advertises a websocket endpoint we connect, authenticate
 * (send login name, then app password), and emit on any `shopping_list_*` message so the UI
 * refreshes instantly. Auto-reconnects with backoff. If notify_push is unavailable the UI's
 * polling covers updates instead.
 */
@Singleton
class RealtimeController @Inject constructor(
    private val service: OcsService,
    private val credentialStore: CredentialStore,
) {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var started = false
    @Volatile private var webSocket: WebSocket? = null

    /** Idempotently establishes (and keeps) the push connection while logged in. */
    fun ensureConnected() {
        if (started) return
        started = true
        connect()
    }

    private fun connect() {
        scope.launch {
            val account = credentialStore.current()
            if (account == null) {
                started = false
                return@launch
            }
            val wsUrl = runCatching {
                service.capabilities().ocs.data.capabilities.notifyPush?.endpoints?.websocket
            }.getOrNull()
            if (wsUrl.isNullOrBlank()) {
                Log.d(TAG, "notify_push unavailable — relying on polling")
                started = false
                return@launch // no push; polling covers it
            }
            Log.d(TAG, "connecting to $wsUrl")
            webSocket = wsClient.newWebSocket(
                Request.Builder().url(wsUrl).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(account.loginName)
                        webSocket.send(account.appPassword)
                        Log.d(TAG, "ws open; sent credentials")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("shopping_list")) {
                            Log.d(TAG, "push received — refreshing")
                            _events.tryEmit(Unit)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.d(TAG, "ws failure: ${t.message}")
                        reconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "ws closed: $reason")
                        reconnect()
                    }
                },
            )
        }
    }

    private fun reconnect() {
        webSocket = null
        scope.launch {
            delay(5_000)
            if (credentialStore.current() != null) connect() else started = false
        }
    }

    private companion object {
        const val TAG = "Realtime"
    }
}
