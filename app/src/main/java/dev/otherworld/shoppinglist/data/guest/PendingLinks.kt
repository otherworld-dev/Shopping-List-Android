package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.domain.guest.parseShareLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A share link the app was opened with, waiting for the screen to pick it up. */
@Singleton
class PendingLinks @Inject constructor() {
    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(uri: String?) {
        if (uri != null && parseShareLink(uri) != null) _link.value = uri
    }

    fun consume() {
        _link.value = null
    }
}
