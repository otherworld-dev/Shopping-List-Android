package dev.otherworld.shoppinglist.ui.common

import androidx.compose.ui.graphics.Color

/** Parses a "#RRGGBB" / "RRGGBB" / "#AARRGGBB" hex string into a Compose [Color], or null. */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val s = hex.trim().removePrefix("#")
    return try {
        when (s.length) {
            6 -> Color(0xFF000000 or s.toLong(16))
            8 -> Color(s.toLong(16))
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
