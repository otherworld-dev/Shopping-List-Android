package dev.otherworld.shoppinglist.data.photo

import dev.otherworld.shoppinglist.data.local.ItemEntity

/*
 * A photo belongs to an item name, so the server puts one upload on every item with that name
 * and one removal takes it off them all. These mirror that on the items already on the phone,
 * so other lists update at once instead of on their next refresh. Ported from the web app's
 * imageSpread.ts.
 */

/** How the server compares item names: trimmed and case-insensitive. */
fun imageNameKey(name: String): String = name.trim().lowercase()

/** The items called [name] that don't show [imageKey] yet, with it given to them. */
fun spreadImageKey(items: List<ItemEntity>, name: String, imageKey: String): List<ItemEntity> {
    val key = imageNameKey(name)
    if (key.isEmpty()) return emptyList()
    return items
        .filter { imageNameKey(it.name) == key && it.imageKey != imageKey }
        .map { it.copy(imageKey = imageKey) }
}

/** The items showing [imageKey] or called [name], with the photo taken off them. */
fun clearImageKeys(items: List<ItemEntity>, imageKey: String?, name: String): List<ItemEntity> {
    val key = imageNameKey(name)
    return items
        .filter { it.imageKey != null }
        .filter { (imageKey != null && it.imageKey == imageKey) || (key.isNotEmpty() && imageNameKey(it.name) == key) }
        .map { it.copy(imageKey = null) }
}
