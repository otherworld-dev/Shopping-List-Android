package dev.otherworld.shoppinglist.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely persists the single connected [Account] in EncryptedSharedPreferences and
 * exposes it as a [StateFlow] so the UI can react to login/logout.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "account_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _account = MutableStateFlow(load())
    val accountFlow: StateFlow<Account?> = _account.asStateFlow()

    fun current(): Account? = _account.value

    fun save(account: Account) {
        prefs.edit()
            .putString(KEY_SERVER, account.server)
            .putString(KEY_LOGIN, account.loginName)
            .putString(KEY_PASSWORD, account.appPassword)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun load(): Account? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Account(server, login, password)
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_LOGIN = "login_name"
        const val KEY_PASSWORD = "app_password"
    }
}
