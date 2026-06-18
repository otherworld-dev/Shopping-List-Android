package dev.otherworld.shoppinglist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListDto(
    val id: Long,
    val title: String = "",
    val userId: String? = null,
    val permission: Int = 1,
    val isOwner: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ItemDto(
    val id: Long,
    val listId: Long,
    val name: String = "",
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val checked: Boolean = false,
    val checkedBy: String? = null,
    val sortOrder: Int = 0,
    val tags: List<TagDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ShopAreaDto(
    val id: Long,
    val listId: Long = 0,
    val name: String = "",
    val sortOrder: Int = 0,
    val color: String? = null,
    val keywords: List<String> = emptyList(),
)

@Serializable
data class TagDto(
    val id: Long,
    val name: String = "",
)

@Serializable
data class CreateTagRequest(val name: String)

// ---- Request bodies (serialized as JSON; null fields are omitted) ----

@Serializable
data class CreateListRequest(val title: String)

@Serializable
data class UpdateListRequest(val title: String)

@Serializable
data class CreateItemRequest(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val areaExplicit: Boolean = false,
)

@Serializable
data class UpdateItemRequest(
    val name: String? = null,
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val sortOrder: Int? = null,
    val areaExplicit: Boolean? = null,
)

@Serializable
data class CheckRequest(val checked: Boolean)

@Serializable
data class ReorderRequest(val sortedIds: List<Long>)

@Serializable
data class CreateAreaRequest(
    val name: String,
    val color: String? = null,
    val keywords: List<String>? = null,
)

@Serializable
data class UpdateAreaRequest(
    val name: String? = null,
    val color: String? = null,
    val sortOrder: Int? = null,
    val keywords: List<String>? = null,
)

@Serializable
data class CopyAreasRequest(val sourceListId: Long)

@Serializable
data class ShareDto(
    val id: Long,
    val listId: Long = 0,
    val sharedWith: String = "",
    val sharedWithType: Int = 0,
    val sharedWithDisplayName: String = "",
    val permission: Int = 0,
    val sharedBy: String = "",
    val token: String? = null,
    val hasPassword: Boolean = false,
    val expiresAt: String? = null,
)

@Serializable
data class CreateShareRequest(
    val sharedWith: String,
    val shareType: Int,
    val permission: Int,
)

@Serializable
data class UpdateShareRequest(val permission: Int)

@Serializable
data class CreateLinkRequest(
    val permission: Int,
    val password: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class UpdateLinkRequest(
    val permission: Int? = null,
    val password: String? = null,
    val removePassword: Boolean? = null,
    val expiresAt: String? = null,
    val removeExpiry: Boolean? = null,
)
