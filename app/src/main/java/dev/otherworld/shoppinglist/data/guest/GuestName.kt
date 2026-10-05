package dev.otherworld.shoppinglist.data.guest

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The longest name the web app's field takes; the server tidies the name again either way. */
private const val MAX_GUEST_NAME = 40

/** A name as it's kept: trimmed and at most [MAX_GUEST_NAME] characters; blank means none. */
fun cleanGuestName(raw: String): String = raw.trim().take(MAX_GUEST_NAME).trim()

/** The name a guest goes by on share links, empty when they haven't given one. */
fun interface GuestName {
    fun get(): String
}

/**
 * One name for every share link, as the web app keeps one per browser. Plain preferences: it's no
 * secret, and like the guest lists themselves it outlives a Nextcloud logout.
 */
@Singleton
class GuestNameStore @Inject constructor(
    @ApplicationContext context: Context,
) : GuestName {
    private val prefs = context.getSharedPreferences("guest_name", Context.MODE_PRIVATE)
    private val _name = MutableStateFlow(prefs.getString(KEY, null).orEmpty())
    val name: StateFlow<String> = _name.asStateFlow()

    override fun get(): String = _name.value

    /** Remembers [raw] tidied, or forgets the name when it's blank. */
    fun set(raw: String) {
        val clean = cleanGuestName(raw)
        if (clean.isEmpty()) prefs.edit().remove(KEY).apply() else prefs.edit().putString(KEY, clean).apply()
        _name.value = clean
    }

    private companion object {
        const val KEY = "guest_name"
    }
}
