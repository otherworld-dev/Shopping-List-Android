package dev.otherworld.shoppinglist.domain.text

import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

/**
 * Faithful Kotlin port of the web app's client-side "smart input" — ingredient parsing,
 * duplicate detection/merging, singular-plural matching, and shop-area auto-detection
 * (`utils/itemMerge.ts`, `utils/fold.ts`, and the parse/detect logic in `ItemEditor.vue`).
 *
 * The server stores items verbatim, so this logic must live on the client to match the web
 * experience. Constructed for a language via [forLanguage]; English enables morphology.
 */
class SmartInput(
    private val pack: ParsingPack = ParsingPack.ENGLISH,
    private val morphology: Boolean = true,
) {
    // ---- Result types ----

    data class ParsedIngredient(val name: String, val quantity: String?, val checked: Boolean = false)

    sealed interface AddPlan {
        /** No existing match — create a new item. */
        data class Create(
            val name: String,
            val quantity: String,
            val shopAreaId: Long?,
            val areaExplicit: Boolean,
            val checked: Boolean = false,
        ) : AddPlan

        /** Matches an existing unchecked item — merge quantities (and maybe pluralize). */
        data class Merge(
            val target: ItemModel,
            val quantity: String,
            val newName: String?,
        ) : AddPlan
    }

    /**
     * Decides what adding [rawInput] should do, given the current [areas] and [existing] items.
     * Returns null if the input parses to an empty name. [explicitAreaId] is the user's manual
     * area pick (null = auto-detect).
     */
    fun planAdd(
        rawInput: String,
        areas: List<ShopAreaModel>,
        existing: List<ItemModel>,
        explicitAreaId: Long?,
    ): AddPlan? {
        val parsed = parseIngredient(rawInput)
        if (parsed.name.isBlank()) return null
        val incoming = parsed.quantity ?: "1"

        // A line pasted as already checked off ("[x] Milk") records something bought, not more
        // of something outstanding, so it never merges into an open item (web app 1.7.1).
        val match = if (parsed.checked) null else findMatchingItem(existing, parsed.name)
        if (match != null) {
            val merged = mergeQuantities(match.quantity, incoming)
            val oldQty = leadingNumber(match.quantity)
            val newQty = leadingNumber(merged)
            val newName = if (oldQty != null && oldQty <= 1.0 && newQty != null && newQty > 1.0) {
                pluralizeName(match.name)
            } else {
                null
            }
            return AddPlan.Merge(match, merged, newName)
        }

        val areaId = explicitAreaId ?: detectArea(parsed.name, areas)
        return AddPlan.Create(parsed.name, incoming, areaId, explicitAreaId != null, parsed.checked)
    }

    // ---- Duplicate detection / normalization ----

    fun findMatchingItem(items: List<ItemModel>, name: String): ItemModel? {
        val normalized = normalizeName(name)
        return items.firstOrNull { !it.checked && normalizeName(it.name) == normalized }
    }

    fun normalizeName(name: String): String {
        val cleaned = fold(name).trim()
            .replace(Regex("\\s*[(（][^)）]*[)）]\\s*"), " ") // strip parentheticals
            .replace(Regex("(?:,\\s|，\\s*).*$"), "")        // strip trailing notes
            .replace(Regex("\\s+"), " ")
            .trim()
        if (!morphology) return cleaned
        return cleaned.split(" ").joinToString(" ") { singularize(it) }
    }

    // ---- Morphology (English) ----

    fun singularize(word: String): String {
        val lower = word.lowercase()
        if (lower.length <= 2 || lower in SINGULAR_EXCEPTIONS) return lower
        if (lower.endsWith("ies")) return lower.dropLast(3) + "y"
        if (lower.endsWith("ves")) {
            val stem = lower.dropLast(3)
            return if (stem.endsWith("l") || stem.endsWith("a") || stem.endsWith("oa")) {
                stem + "f"
            } else {
                stem + "fe"
            }
        }
        if (lower.endsWith("shes") || lower.endsWith("ches") || lower.endsWith("sses") ||
            lower.endsWith("xes") || lower.endsWith("zes")
        ) {
            return lower.dropLast(2)
        }
        if (lower.endsWith("oes") && lower != "shoes") return lower.dropLast(2)
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us")) return lower.dropLast(1)
        return lower
    }

    fun pluralizeName(name: String): String {
        if (!morphology) return name
        val words = name.split(" ").toMutableList()
        val last = words.last()
        val lower = last.lowercase()
        if (lower.length <= 1 || lower in SINGULAR_EXCEPTIONS) return name
        if (singularize(lower) != lower) return name // already plural

        val plural = when {
            lower.endsWith("y") && lower.length >= 2 && lower[lower.length - 2] !in "aeiou" ->
                last.dropLast(1) + "ies"
            lower.endsWith("fe") -> last.dropLast(2) + "ves"
            lower.endsWith("f") && !lower.endsWith("ff") -> last.dropLast(1) + "ves"
            Regex("(?:ch|sh|ss|x|z|o)$").containsMatchIn(lower) -> last + "es"
            else -> last + "s"
        }
        words[words.size - 1] = plural
        return words.joinToString(" ")
    }

    // ---- Quantity merging ----

    private data class ParsedQuantity(val number: Double?, val unit: String, val raw: String)

    private fun canonicalUnit(unit: String): String {
        val lower = unit.lowercase()
        return pack.unitAliases[lower] ?: lower
    }

    private fun evalNumber(s: String): Double? {
        Regex("^(\\d+)\\s+(\\d+)/(\\d+)$").find(s)?.let { m ->
            val (a, b, c) = m.destructured
            return a.toInt() + b.toInt().toDouble() / c.toInt()
        }
        Regex("^(\\d+)/(\\d+)$").find(s)?.let { m ->
            val (b, c) = m.destructured
            return b.toInt().toDouble() / c.toInt()
        }
        return s.toDoubleOrNull()
    }

    private fun parseQuantity(qty: String?): ParsedQuantity {
        if (qty.isNullOrBlank()) return ParsedQuantity(null, "", qty ?: "")
        val trimmed = qty.trim()
        val m = Regex("^(\\d+(?:\\s+\\d+/\\d+|\\.\\d+|/\\d+)?)\\s*(.*)$").find(trimmed)
            ?: return ParsedQuantity(null, "", trimmed)
        val number = evalNumber(m.groupValues[1].trim())
        val unit = m.groupValues[2].trim().lowercase()
        return ParsedQuantity(number, unit, trimmed)
    }

    fun mergeQuantities(existing: String?, incoming: String?): String {
        if (existing.isNullOrBlank() && incoming.isNullOrBlank()) return ""
        if (existing.isNullOrBlank()) return incoming!!
        if (incoming.isNullOrBlank()) return existing

        val a = parseQuantity(existing)
        val b = parseQuantity(incoming)
        if (a.number != null && b.number != null && canonicalUnit(a.unit) == canonicalUnit(b.unit)) {
            val sum = a.number + b.number
            val formatted = if (sum == floor(sum) && !sum.isInfinite()) {
                sum.toLong().toString()
            } else {
                BigDecimal(sum).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            }
            return if (a.unit.isNotEmpty()) "$formatted ${a.unit}" else formatted
        }
        return "${a.raw} + ${b.raw}"
    }

    /** Parses the leading number of a quantity string (mirrors JS parseFloat semantics). */
    private fun leadingNumber(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val m = Regex("^[+-]?\\d*\\.?\\d+").find(s.trim()) ?: return null
        return m.value.toDoubleOrNull()
    }

    // ---- Ingredient parsing ----

    private val prepositionRe: Regex? =
        if (pack.prepositions.isNotEmpty()) {
            Regex("^(?:" + pack.prepositions.joinToString("|") { Regex.escape(it) } + ")\\s+", RegexOption.IGNORE_CASE)
        } else {
            null
        }

    private val qtyPattern: Regex = run {
        val commaDecimal = pack.decimalSeparators.contains(",")
        val dec = if (commaDecimal) "[.,]" else "\\."
        Regex("^(\\d+(?:\\s+\\d+/\\d+|/\\d+|$dec\\d+)?(?:\\s*-\\s*\\d+(?:/\\d+|$dec\\d+)?)?)\\s*")
    }

    private fun matchUnit(text: String): String {
        val lower = text.lowercase()
        var best = ""
        for (unit in pack.units) {
            if (lower.startsWith("$unit ") || lower.startsWith("$unit,") || lower == unit) {
                if (unit.length > best.length) best = unit
            }
        }
        return best
    }

    private fun cleanName(raw: String): String {
        var name = raw
        var prev = ""
        while (prev != name) {
            prev = name
            name = name.replace(Regex("\\s*\\([^)]*\\)"), "")
        }
        name = name
            .replace(Regex("[()]"), "")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex(",\\s*$"), "")
            .replace(Regex("^\\s*,\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Capitalize the first letter, but leave intercapped product names alone: an uppercase
        // second letter means the case is deliberate ("iPhone", "eBay"), and forcing it would
        // corrupt the name.
        if (name.isNotEmpty()) {
            val second = name.getOrNull(1)?.toString()
            val secondIsUpper = second != null && second != second.lowercase()
            if (!secondIsUpper) name = name[0].uppercaseChar() + name.substring(1)
        }
        return name
    }

    private fun stripConnector(rest: String): String {
        var r = rest.replace(Regex("^,\\s*"), "")
        if (prepositionRe != null) r = r.replace(prepositionRe, "")
        return r.trim()
    }

    private data class MarkupResult(val rest: String, val checked: Boolean)

    /**
     * Normalises the list markup people paste in from notes apps and chats, so the quantity
     * logic in [parseLine] sees a plain line, and reports whether the line carried a ticked
     * checkbox so "[x] Milk" can import as already checked off.
     *
     * A bracketed number is rewritten rather than removed, because it IS the quantity:
     * "[ 10 ] Aepfel" becomes "10 Aepfel". An ordered-list index is removed rather than read
     * as a quantity, because "1." numbers the line, it doesn't count the item. Markers can nest
     * in either order ("[x] - Milk" from a chat, "- [x] Milk" from a Markdown checklist), so
     * the passes repeat until the line stops changing.
     */
    private fun readListMarkup(line: String): MarkupResult {
        var out = line.trim()
        var checked = false
        var prev = ""
        while (prev != out) {
            prev = out
            // [ ] / [x] / [X] checkbox. Deliberately does not match "[ 10 ]".
            CHECKBOX_MARKUP.find(out)?.let { box ->
                if (box.groupValues[1].isNotEmpty()) checked = true
                out = out.substring(box.value.length)
            }
            // [ 10 ] bracketed quantity -> plain leading quantity.
            out = out.replace(BRACKETED_QTY_MARKUP, "$1 ")
            // Bullet markers. The trailing space requirement keeps "-3" intact.
            out = out.replace(BULLET_MARKUP, "")
            // Ordered-list index. Requires the separator and a space, so "1.5 kg" is untouched.
            out = out.replace(ORDERED_INDEX_MARKUP, "")
        }
        return MarkupResult(out.trim(), checked)
    }

    fun stripListMarkup(line: String): String = readListMarkup(line).rest

    fun parseIngredient(line: String): ParsedIngredient {
        val markup = readListMarkup(line)
        return parseLine(markup.rest).copy(checked = markup.checked)
    }

    /** The quantity and name logic, on a line whose list markup is already gone. */
    private fun parseLine(trimmed: String): ParsedIngredient {
        if (trimmed.isEmpty()) return ParsedIngredient("", null)

        val trimmedLower = trimmed.lowercase()
        for (unit in pack.leadingUnits) {
            if (trimmedLower.startsWith("$unit ") || trimmedLower.startsWith("$unit,")) {
                val rest = stripConnector(trimmed.substring(unit.length).trim())
                return ParsedIngredient(cleanName(rest.ifEmpty { trimmed }), "1 $unit")
            }
        }

        val match = qtyPattern.find(trimmed)
            ?: return ParsedIngredient(cleanName(trimmed), null)

        val commaDecimal = pack.decimalSeparators.contains(",")
        val qtyStr = if (commaDecimal) match.groupValues[1].trim().replace(",", ".") else match.groupValues[1].trim()
        var rest = trimmed.substring(match.value.length).trim()

        // "2x Milk" / "2 x Milk" / "2 × Milk": the x is a count marker, not part of the name.
        // Everyday notation in English and German alike.
        val mult = MULTIPLIER.find(rest)
        if (mult != null) {
            rest = rest.substring(mult.value.length).trim()
            return ParsedIngredient(cleanName(rest.ifEmpty { trimmed }), qtyStr)
        }

        // Digits glued straight onto a word only count as a quantity when that word is a unit:
        // "500ml Milk" stays a quantity, but "7up", "7-Eleven" and "3M tape" are names, and
        // splitting them would corrupt them. A comma is a separator ("10, Aepfel"), not a glued
        // word.
        val qtyLen = match.groupValues[1].length
        val attached = trimmed.length > qtyLen && !(trimmed[qtyLen].isWhitespace() || trimmed[qtyLen] == ',')
        if (attached && matchUnit(rest).isEmpty()) {
            return ParsedIngredient(cleanName(trimmed), null)
        }

        val matchedUnit = matchUnit(rest)
        var finalQty = qtyStr
        if (matchedUnit.isNotEmpty()) {
            finalQty = "$qtyStr $matchedUnit"
            rest = stripConnector(rest.substring(matchedUnit.length).trim())
        }
        rest = rest.replace(Regex("^,\\s*"), "").trim()

        return ParsedIngredient(cleanName(rest.ifEmpty { trimmed }), finalQty)
    }

    // ---- Area auto-detection ----

    fun detectArea(ingredientName: String, areas: List<ShopAreaModel>): Long? {
        val needle = fold(ingredientName)
        var bestArea: Long? = null
        var bestLen = 0
        for (area in areas.sortedBy { it.sortOrder }) {
            for (keyword in area.keywords) {
                if (keyword.isBlank()) continue
                val k = fold(keyword)
                if (k.length > bestLen && needle.contains(k)) {
                    bestLen = k.length
                    bestArea = area.id
                }
            }
        }
        return bestArea
    }

    companion object {
        private val SINGULAR_EXCEPTIONS = setOf(
            "asparagus", "hummus", "couscous", "cheese", "rice", "juice",
            "lettuce", "sauce", "produce", "grease", "mousse",
        )

        /** English enables morphology; everything else uses the English pack without it. */
        fun forLanguage(language: String): SmartInput {
            val isEnglish = language.lowercase().startsWith("en")
            return SmartInput(pack = ParsingPack.ENGLISH, morphology = isEnglish)
        }

        fun english() = SmartInput(ParsingPack.ENGLISH, morphology = true)

        /**
         * Splits pasted text into candidate item lines: any line-ending style, trimmed, blanks
         * dropped (mirrors the web app's paste handler).
         */
        fun splitLines(text: String): List<String> =
            text.split(LINE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

        private val LINE_BREAK = Regex("\\r?\\n")
        private val CHECKBOX_MARKUP = Regex("^\\[\\s*([xX]?)\\s*\\]\\s*")
        private val BRACKETED_QTY_MARKUP = Regex("^\\[\\s*(\\d+(?:[.,]\\d+)?)\\s*\\]\\s*")
        private val BULLET_MARKUP = Regex("^[-*•]\\s+")
        private val ORDERED_INDEX_MARKUP = Regex("^\\d+[.)]\\s+")
        private val MULTIPLIER = Regex("^[x×]\\s+", RegexOption.IGNORE_CASE)
    }
}
