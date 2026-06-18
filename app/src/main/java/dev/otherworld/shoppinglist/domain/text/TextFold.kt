package dev.otherworld.shoppinglist.domain.text

import java.text.Normalizer

/**
 * Language-neutral text folding for matching and duplicate detection — a direct port of the
 * web app's `utils/fold.ts`. Strips only the Latin combining-diacritics block (U+0300–U+036F)
 * after NFD and lowercases locale-independently, so "Café" -> "cafe", "Jalapeño" -> "jalapeno",
 * while leaving non-Latin scripts (e.g. kana) untouched.
 */
fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(LATIN_COMBINING_MARKS, "")
        .lowercase()

private val LATIN_COMBINING_MARKS = Regex("[̀-ͯ]")
