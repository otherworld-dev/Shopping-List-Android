package dev.otherworld.shoppinglist.data.auth

import dev.otherworld.shoppinglist.di.LoginClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/**
 * Implements the Nextcloud Login Flow v2 handshake:
 *   1. POST /index.php/login/v2  -> { login, poll: { endpoint, token } }
 *   2. open `login` in a browser; user authenticates and grants the app
 *   3. POST poll.endpoint with the token until it returns { server, loginName, appPassword }
 *
 * See https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html
 */
class LoginFlowV2Client @Inject constructor(
    @LoginClient private val client: OkHttpClient,
    private val json: Json,
) {
    data class Init(
        val loginUrl: String,
        val pollEndpoint: String,
        val pollToken: String,
    )

    /** Starts the flow against [serverUrl] (with or without scheme/trailing slash). */
    suspend fun start(serverUrl: String): Init = withContext(Dispatchers.IO) {
        val base = normalizeServer(serverUrl)
        val request = Request.Builder()
            .url("$base/index.php/login/v2")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Login init failed (HTTP ${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val poll = root["poll"]!!.jsonObject
            Init(
                loginUrl = root["login"]!!.jsonPrimitive.content,
                pollEndpoint = poll["endpoint"]!!.jsonPrimitive.content,
                pollToken = poll["token"]!!.jsonPrimitive.content,
            )
        }
    }

    /** Polls once. Returns an [Account] when authorized, or null while still pending. */
    suspend fun poll(endpoint: String, token: String): Account? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("token", token).build()
        val request = Request.Builder().url(endpoint).post(form).build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> {
                    val root = json.parseToJsonElement(response.body!!.string()).jsonObject
                    Account(
                        server = root["server"]!!.jsonPrimitive.content,
                        loginName = root["loginName"]!!.jsonPrimitive.content,
                        appPassword = root["appPassword"]!!.jsonPrimitive.content,
                    )
                }
                404 -> null // not authorized yet
                else -> throw IOException("Login poll failed (HTTP ${response.code})")
            }
        }
    }

    private fun normalizeServer(input: String): String {
        var s = input.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://$s"
        }
        return s
    }
}
