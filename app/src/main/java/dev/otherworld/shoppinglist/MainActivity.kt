package dev.otherworld.shoppinglist

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.otherworld.shoppinglist.data.guest.PendingLinks
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.prefs.TextScale
import dev.otherworld.shoppinglist.data.theme.ServerTheme
import dev.otherworld.shoppinglist.ui.AppRoot
import dev.otherworld.shoppinglist.ui.common.parseHexColor
import dev.otherworld.shoppinglist.ui.theme.AppBackground
import dev.otherworld.shoppinglist.ui.theme.DefaultBrand
import dev.otherworld.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serverTheme: ServerTheme
    @Inject lateinit var displayPrefs: DisplayPrefs
    @Inject lateinit var pendingLinks: PendingLinks

    /** The text size this activity was built with; choosing another rebuilds it. */
    private var appliedTextScale = TextScale.DEFAULT

    // Text size from Settings, on top of the phone's own font scale. It goes on the activity's
    // configuration rather than into Compose, because dialogs, menus and sheets are windows of
    // their own that read their density afresh from it; dp layout sizes stay put.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        appliedTextScale = DisplayPrefs.storedTextScale(newBase)
        val system = newBase.resources.configuration.fontScale
        applyOverrideConfiguration(Configuration().apply { fontScale = appliedTextScale.fontScaleOn(system) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            displayPrefs.textScale.collect { if (it != appliedTextScale) recreate() }
        }
        // Only on a fresh start: a recreated activity (rotation) would offer the same link again,
        // and so would Recents, which relaunches with the intent the task was first opened with.
        val fromHistory = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) pendingLinks.offer(intent?.dataString)
        setContent {
            val brandHex by serverTheme.brandHex.collectAsStateWithLifecycle()
            val brand = parseHexColor(brandHex) ?: DefaultBrand
            val themeMode by displayPrefs.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark(isSystemInDarkTheme())
            // The bars follow the app's theme, not the phone's: with Dark forced on a light
            // phone, SystemBarStyle.auto would put dark icons on the dark gradient.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ShoppingListTheme(brandColor = brand, dark = dark) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    AppBackground {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingLinks.offer(intent.dataString)
    }
}
