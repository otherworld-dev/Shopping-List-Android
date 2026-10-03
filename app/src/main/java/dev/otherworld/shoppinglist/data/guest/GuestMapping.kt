package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.AreaEntity
import dev.otherworld.shoppinglist.data.local.GuestIdKind
import dev.otherworld.shoppinglist.data.local.ItemEntity
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto

internal suspend fun mapGuestAreas(dtos: List<ShopAreaDto>, shareId: Long, listId: Long, ids: GuestIdLookup): List<AreaEntity> =
    dtos.map {
        AreaEntity(
            id = ids.localId(shareId, GuestIdKind.AREA, it.id),
            listId = listId,
            name = it.name,
            sortOrder = it.sortOrder,
            color = it.color,
            keywords = it.keywords,
        )
    }

internal suspend fun mapGuestItems(dtos: List<ItemDto>, shareId: Long, listId: Long, ids: GuestIdLookup): List<ItemEntity> =
    dtos.map {
        ItemEntity(
            id = ids.localId(shareId, GuestIdKind.ITEM, it.id),
            listId = listId,
            name = it.name,
            quantity = it.quantity,
            unit = it.unit,
            shopAreaId = it.shopAreaId?.let { area -> ids.localId(shareId, GuestIdKind.AREA, area) },
            checked = it.checked,
            checkedBy = it.checkedBy,
            sortOrder = it.sortOrder,
            updatedAt = it.updatedAt,
            imageKey = it.imageKey,
        )
    }
