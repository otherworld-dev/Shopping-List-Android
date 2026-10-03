package dev.otherworld.shoppinglist.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the user chose to use the app without a Nextcloud account. Once set it keeps the app
 * past the login screen, even with no lists and after logging out of an account added later.
 */
@Singleton
class LocalMode @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("local_mode", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        _enabled.value = true
    }

    private companion object {
        const val KEY_ENABLED = "use_without_account"
    }
}
