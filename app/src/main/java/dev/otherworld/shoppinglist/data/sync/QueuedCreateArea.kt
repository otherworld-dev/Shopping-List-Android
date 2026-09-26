package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.text.SmartInput

/**
 * The shop area a queued item create is sent with. The server never detects areas itself, so
 * an item added before its list's areas reached the phone is detected here instead, against
 * the areas known by the time it syncs. A picked area, or one the add box already detected,
 * is sent as it is.
 */
internal fun areaForQueuedCreate(
    payload: ItemCreatePayload,
    areas: List<ShopAreaModel>,
    smartInput: SmartInput,
): Long? {
    if (!payload.detectArea || payload.areaExplicit || payload.shopAreaId != null) return payload.shopAreaId
    return smartInput.detectArea(payload.name, areas)
}
