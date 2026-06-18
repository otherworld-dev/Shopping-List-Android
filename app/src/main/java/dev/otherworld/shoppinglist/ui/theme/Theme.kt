package dev.otherworld.shoppinglist.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The Nextcloud default brand colour, used until the server's theme is fetched. */
val DefaultBrand = Color(0xFF0082C9)

/** The connected server's brand colour, available everywhere for accents/background. */
val LocalBrandColor = staticCompositionLocalOf { DefaultBrand }

@Composable
fun ShoppingListTheme(
    brandColor: Color = DefaultBrand,
    content: @Composable () -> Unit,
) {
    val brandLight = lerp(brandColor, Color.White, 0.30f)
    val scheme = darkColorScheme(
        primary = brandLight,
        onPrimary = Color(0xFF06140A),
        primaryContainer = brandColor,
        onPrimaryContainer = Color.White,
        secondary = brandLight,
        onSecondary = Color(0xFF06140A),
        tertiary = brandLight,
        background = lerp(brandColor, NcBgMid, 0.88f),
        onBackground = NcOnSurface,
        surface = NcCard,
        onSurface = NcOnSurface,
        surfaceVariant = NcElevated,
        onSurfaceVariant = NcOnSurfaceVariant,
        surfaceContainer = NcElevated,
        surfaceContainerHigh = NcElevated,
        outline = NcOutline,
        outlineVariant = NcOutline,
        error = NcError,
        onError = Color(0xFF3A0A08),
    )
    CompositionLocalProvider(LocalBrandColor provides brandColor) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** Deep, brand-tinted dark gradient with a soft brand-coloured glow, drawn behind the app. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val brand = LocalBrandColor.current
    val top = lerp(brand, Color(0xFF0A0D15), 0.80f)
    val mid = lerp(brand, Color(0xFF080A11), 0.90f)
    val bottom = Color(0xFF05070C)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, mid, bottom))),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(brand.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(200f, 760f),
                        radius = 950f,
                    ),
                ),
        )
        content()
    }
}
