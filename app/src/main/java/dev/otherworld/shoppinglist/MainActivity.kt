package dev.otherworld.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
