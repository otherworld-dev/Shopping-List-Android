package dev.otherworld.shoppinglist.data.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.remote.OcsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls the Nextcloud instance's brand colour from `capabilities.theming.color` and exposes it
 * so the UI can adopt the server's theme (e.g. this server is green `#3B8338`). The last value
 * is cached so the themed colour is applied immediately on the next launch.
 */
@Singleton
class ServerTheme @Inject constructor(
    @ApplicationContext context: Context,
    private val service: OcsService,
    private val credentialStore: CredentialStore,
) {
    private val prefs = context.getSharedPreferences("server_theme", Context.MODE_PRIVATE)

    private val _brandHex = MutableStateFlow(prefs.getString(KEY, null))
    val brandHex: StateFlow<String?> = _brandHex.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh() {
        if (credentialStore.current() == null) return
        scope.launch {
            val color = runCatching {
                service.capabilities().ocs.data.capabilities.theming?.color
            }.getOrNull()
            if (!color.isNullOrBlank()) {
                _brandHex.value = color
                prefs.edit().putString(KEY, color).apply()
            }
        }
    }

    fun clear() {
        _brandHex.value = null
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "brand_color"
    }
}
