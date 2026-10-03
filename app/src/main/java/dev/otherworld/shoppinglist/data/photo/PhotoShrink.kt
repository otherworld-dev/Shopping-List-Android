package dev.otherworld.shoppinglist.data.photo

import kotlin.math.max
import kotlin.math.roundToInt

/** The longest edge a photo is shrunk to before upload; the server keeps the same bound. */
const val PHOTO_MAX_EDGE = 1280

/** JPEG quality for the shrunk photo, the same as the web app's. */
const val PHOTO_JPEG_QUALITY = 85

/** The size a [width] x [height] photo is shrunk to so its longest edge fits [maxEdge]; never larger. */
fun shrunkSize(width: Int, height: Int, maxEdge: Int = PHOTO_MAX_EDGE): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longest
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * The power-of-two step to decode at, so a 12 megapixel picture is never held at full size:
 * the largest one that still leaves the longest edge at least [maxEdge].
 */
fun decodeSampleSize(width: Int, height: Int, maxEdge: Int = PHOTO_MAX_EDGE): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}
