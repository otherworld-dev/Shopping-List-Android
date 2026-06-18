package dev.otherworld.shoppinglist.data.local

import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ListDto
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel

// ---- Entity -> domain model ----

fun ListEntity.toModel() = ShoppingListModel(
    id = id,
    title = title,
    permission = permission,
    isOwner = isOwner,
)

fun ItemEntity.toModel() = ItemModel(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    unit = unit,
    shopAreaId = shopAreaId,
    checked = checked,
    checkedBy = checkedBy,
    sortOrder = sortOrder,
    tags = emptyList(),
)

fun AreaEntity.toModel() = ShopAreaModel(
    id = id,
    listId = listId,
    name = name,
    sortOrder = sortOrder,
    color = color,
    keywords = keywords,
)

// ---- DTO -> entity (used when refreshing from the server) ----

fun ListDto.toEntity(sortOrder: Int) = ListEntity(
    id = id,
    title = title,
    permission = permission,
    isOwner = isOwner,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun ItemDto.toEntity() = ItemEntity(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    unit = unit,
    shopAreaId = shopAreaId,
    checked = checked,
    checkedBy = checkedBy,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

fun ShopAreaDto.toEntity(listId: Long) = AreaEntity(
    id = id,
    listId = listId,
    name = name,
    sortOrder = sortOrder,
    color = color,
    keywords = keywords,
)
