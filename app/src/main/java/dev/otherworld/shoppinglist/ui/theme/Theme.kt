package dev.otherworld.shoppinglist.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

/** Whether the app is drawn dark, for the few places that differ beyond the colour scheme. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** The shade behind every other row in the lists, so long lists stay easy to follow. */
val LocalRowShade = staticCompositionLocalOf { NcRowAlt }

@Composable
fun ShoppingListTheme(
    brandColor: Color = DefaultBrand,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBrandColor provides brandColor,
        LocalDarkTheme provides dark,
        LocalRowShade provides if (dark) NcRowAlt else NcLightRowAlt,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(brandColor) else lightScheme(brandColor),
            typography = Typography(),
            content = content,
        )
    }
}

private fun darkScheme(brand: Color): ColorScheme {
    val brandLight = lerp(brand, Color.White, 0.30f)
    return darkColorScheme(
        primary = brandLight,
        onPrimary = Color(0xFF06140A),
        primaryContainer = brand,
        onPrimaryContainer = Color.White,
        secondary = brandLight,
        onSecondary = Color(0xFF06140A),
        tertiary = brandLight,
        background = lerp(brand, NcBgMid, 0.88f),
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
}

/**
 * Brand colour darkened for text and controls on white, so pale brands stay readable. Every
 * container slot is set: the ones left out fall back to Material's own purple-tinted greys.
 */
private fun lightScheme(brand: Color): ColorScheme {
    val brandDark = lerp(brand, Color.Black, 0.20f)
    val brandPale = lerp(brand, Color.White, 0.82f)
    return lightColorScheme(
        primary = brandDark,
        onPrimary = Color.White,
        primaryContainer = brand,
        onPrimaryContainer = Color.White,
        secondary = brandDark,
        onSecondary = Color.White,
        secondaryContainer = brandPale,
        onSecondaryContainer = NcLightOnSurface,
        tertiary = brandDark,
        onTertiary = Color.White,
        tertiaryContainer = brandPale,
        onTertiaryContainer = NcLightOnSurface,
        background = lerp(brand, NcLightPage, 0.94f),
        onBackground = NcLightOnSurface,
        surface = NcLightCard,
        onSurface = NcLightOnSurface,
        surfaceVariant = NcLightContainer,
        onSurfaceVariant = NcLightOnSurfaceVariant,
        surfaceTint = brandDark,
        surfaceBright = NcLightCard,
        surfaceDim = NcLightContainerHigh,
        surfaceContainerLowest = NcLightCard,
        surfaceContainerLow = NcLightRowAlt,
        surfaceContainer = NcLightCard,
        surfaceContainerHigh = NcLightCard,
        surfaceContainerHighest = NcLightContainerHigh,
        outline = NcLightOutline,
        outlineVariant = NcLightOutline,
        error = NcLightError,
        onError = Color.White,
    )
}

/** Brand-tinted gradient with a soft brand-coloured glow, drawn behind the app. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val brand = LocalBrandColor.current
    val dark = LocalDarkTheme.current
    val stops = if (dark) {
        listOf(lerp(brand, Color(0xFF0A0D15), 0.80f), lerp(brand, Color(0xFF080A11), 0.90f), Color(0xFF05070C))
    } else {
        listOf(lerp(brand, NcLightPage, 0.88f), lerp(brand, NcLightPage, 0.95f), NcLightPage)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(stops)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(brand.copy(alpha = if (dark) 0.22f else 0.10f), Color.Transparent),
                        center = Offset(200f, 760f),
                        radius = 950f,
                    ),
                ),
        )
        content()
    }
}
