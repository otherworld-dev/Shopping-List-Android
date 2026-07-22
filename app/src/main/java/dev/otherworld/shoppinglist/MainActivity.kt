package dev.otherworld.shoppinglist

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark (navy gradient), so pin the system bars to light icons
        // rather than letting them follow the system light/dark setting. Targeting SDK 36
        // otherwise leaves dark icons sitting on the dark background, unreadable.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val brandHex by serverTheme.brandHex.collectAsStateWithLifecycle()
            val brand = parseHexColor(brandHex) ?: DefaultBrand
            ShoppingListTheme(brandColor = brand) {
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
