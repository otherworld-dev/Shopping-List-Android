package dev.otherworld.shoppinglist.data.guest

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Passwords of share links, by share id. */
interface GuestPasswords {
    fun get(shareId: Long): String?
    fun put(shareId: Long, password: String)
    fun remove(shareId: Long)
}

/** Its own encrypted file, so logging out of Nextcloud (which clears account_prefs) keeps them. */
@Singleton
class GuestPasswordStore @Inject constructor(
    @ApplicationContext context: Context,
) : GuestPasswords {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "guest_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(shareId: Long): String? = prefs.getString(key(shareId), null)

    override fun put(shareId: Long, password: String) {
        prefs.edit().putString(key(shareId), password).apply()
    }

    override fun remove(shareId: Long) {
        prefs.edit().remove(key(shareId)).apply()
    }

    private fun key(shareId: Long) = "password_$shareId"
}
