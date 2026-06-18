package dev.otherworld.shoppinglist.domain.text

/**
 * Locale-specific parsing data — a port of the web app's per-language `resources/parsing` JSON.
 * Only English is bundled here for now; other languages fall back to English (matching the
 * web app's behaviour), and morphology (singular/plural) only applies to English.
 */
data class ParsingPack(
    val units: List<String>,
    val unitAliases: Map<String, String>,
    val leadingUnits: List<String>,
    val prepositions: List<String>,
    val decimalSeparators: List<String>,
) {
    companion object {
        val ENGLISH = ParsingPack(
            units = listOf(
                "teaspoon", "teaspoons", "tsp",
                "tablespoon", "tablespoons", "tbsp",
                "cup", "cups",
                "ounce", "ounces", "oz",
                "pound", "pounds", "lb", "lbs",
                "gram", "grams", "g",
                "kilogram", "kilograms", "kg",
                "milliliter", "milliliters", "ml",
                "liter", "liters", "l",
                "pinch", "pinches",
                "bunch", "bunches",
                "clove", "cloves",
                "can", "cans",
                "bottle", "bottles",
                "piece", "pieces",
                "slice", "slices",
                "head", "heads",
                "stalk", "stalks",
                "sprig", "sprigs",
                "pack", "packs", "packet", "packets",
                "bag", "bags",
                "fl oz",
            ),
            unitAliases = mapOf(
                "cups" to "cup", "teaspoons" to "teaspoon", "tsp" to "teaspoon",
                "tablespoons" to "tablespoon", "tbsp" to "tablespoon",
                "ounces" to "ounce", "oz" to "ounce",
                "pounds" to "pound", "lbs" to "pound", "lb" to "pound",
                "grams" to "gram", "g" to "gram",
                "kilograms" to "kilogram", "kg" to "kilogram",
                "milliliters" to "milliliter", "ml" to "milliliter",
                "liters" to "liter", "l" to "liter",
                "cans" to "can", "bottles" to "bottle", "slices" to "slice",
                "pieces" to "piece", "cloves" to "clove",
                "stalks" to "stalk", "sprigs" to "sprig",
                "bags" to "bag", "packs" to "pack", "packets" to "pack",
                "pinches" to "pinch", "bunches" to "bunch",
                "heads" to "head",
            ),
            leadingUnits = listOf("pinch", "pinches", "bunch", "bunches", "zest", "dash", "handful"),
            prepositions = listOf("of"),
            decimalSeparators = listOf("."),
        )
    }
}
