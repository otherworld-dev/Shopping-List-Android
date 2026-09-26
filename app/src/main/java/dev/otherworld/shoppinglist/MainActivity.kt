package dev.otherworld.shoppinglist

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
import dagger.hilt.android.AndroidEntryPoint
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.theme.ServerTheme
import dev.otherworld.shoppinglist.ui.AppRoot
import dev.otherworld.shoppinglist.ui.common.parseHexColor
import dev.otherworld.shoppinglist.ui.theme.AppBackground
import dev.otherworld.shoppinglist.ui.theme.DefaultBrand
import dev.otherworld.shoppinglist.ui.theme.ShoppingListTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serverTheme: ServerTheme
    @Inject lateinit var displayPrefs: DisplayPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
