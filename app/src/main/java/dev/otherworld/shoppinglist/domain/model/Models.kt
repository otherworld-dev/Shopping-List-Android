package dev.otherworld.shoppinglist.domain.model

import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ListDto
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import dev.otherworld.shoppinglist.data.remote.dto.TagDto

/** Permission levels mirroring the server (0 = read-only, 1 = read/write). */
object Permission {
    const val READ = 0
    const val WRITE = 1
}

data class ShoppingListModel(
    val id: Long,
    val title: String,
    val permission: Int,
    val isOwner: Boolean,
) {
    val canWrite: Boolean get() = permission >= Permission.WRITE
}

data class TagModel(
    val id: Long,
    val name: String,
)

data class ItemModel(
    val id: Long,
    val listId: Long,
    val name: String,
    val quantity: String?,
    val unit: String?,
    val shopAreaId: Long?,
    val checked: Boolean,
    val checkedBy: String?,
    val sortOrder: Int,
    val tags: List<TagModel> = emptyList(),
) {
    /** Quantity worth displaying — hidden when absent or the implicit default of "1". */
    val displayQuantity: String?
        get() {
            val q = quantity?.trim().orEmpty()
            val combined = listOfNotNull(q.ifBlank { null }, unit?.trim()?.ifBlank { null })
                .joinToString(" ")
            return combined.ifBlank { null }?.takeUnless { it == "1" }
        }
}

data class ShopAreaModel(
    val id: Long,
    val listId: Long,
    val name: String,
    val sortOrder: Int,
    val color: String?,
    val keywords: List<String>,
)

object ShareType {
    const val USER = 0
    const val GROUP = 1
    const val LINK = 3
}

data class ShareModel(
    val id: Long,
    val sharedWith: String,
    val type: Int,
    val displayName: String,
    val permission: Int,
    val token: String?,
    val hasPassword: Boolean,
    val expiresAt: String?,
) {
    val isLink: Boolean get() = type == ShareType.LINK
    val canWrite: Boolean get() = permission >= Permission.WRITE
}

// ---- Mappers ----

fun ListDto.toModel() = ShoppingListModel(
    id = id,
    title = title,
    permission = permission,
    isOwner = isOwner,
)

fun TagDto.toModel() = TagModel(id = id, name = name)

fun ItemDto.toModel() = ItemModel(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    unit = unit,
    shopAreaId = shopAreaId,
    checked = checked,
    checkedBy = checkedBy,
    sortOrder = sortOrder,
    tags = tags.map { it.toModel() },
)

fun ShopAreaDto.toModel() = ShopAreaModel(
    id = id,
    listId = listId,
    name = name,
    sortOrder = sortOrder,
    color = color,
    keywords = keywords,
)

fun dev.otherworld.shoppinglist.data.remote.dto.ShareDto.toModel() = ShareModel(
    id = id,
    sharedWith = sharedWith,
    type = sharedWithType,
    displayName = sharedWithDisplayName.ifBlank { sharedWith },
    permission = permission,
    token = token,
    hasPassword = hasPassword,
    expiresAt = expiresAt,
)
