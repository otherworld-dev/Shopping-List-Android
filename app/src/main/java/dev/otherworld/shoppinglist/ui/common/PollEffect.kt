package dev.otherworld.shoppinglist.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Invokes [onPoll] every [intervalMs] while the screen is at least STARTED, automatically
 * pausing when the screen is backgrounded or left. The fallback for real-time updates when
 * notify_push is unavailable (matching the web app's 10s polling).
 */
@Composable
fun PollEffect(intervalMs: Long = 10_000L, onPoll: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalMs)
                onPoll()
            }
        }
    }
}
