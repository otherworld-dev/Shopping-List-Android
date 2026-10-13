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

/**
 * The area a new item's create is queued with, given the one the phone shows it under. The
 * list's default area is shown but never sent: the phone's copy of it can be out of date, and
 * an area that is sent beats the server's own default, so a stale one would stick. Sent with
 * none, the item gets the server's current default (see [areaAfterCreate]).
 */
internal fun areaToSend(shownAreaId: Long?, isListDefault: Boolean): Long? = shownAreaId.takeUnless { isListDefault }

/**
 * The area an item has once its create has synced. One sent with no area may come back with
 * the list's default area, which the server (app 1.12.0 and later) gives to items no keyword
 * placed. An area the item already has here is kept: it was detected or picked when the item
 * was added, or set since by an edit that is queued behind the create.
 */
internal fun areaAfterCreate(localAreaId: Long?, serverAreaId: Long?): Long? = localAreaId ?: serverAreaId
