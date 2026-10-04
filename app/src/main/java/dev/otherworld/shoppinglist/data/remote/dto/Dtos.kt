package dev.otherworld.shoppinglist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListDto(
    val id: Long,
    val title: String = "",
    val userId: String? = null,
    val permission: Int = 1,
    val isOwner: Boolean = true,
    // Since server app 1.8.0; older servers don't send it.
    val isPinned: Boolean? = null,
    // Since server app 1.10.0: this user's own place in the Custom order.
    val position: Int? = null,
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
    // Since server app 1.9.0: the key of the item's photo, null when it has none.
    val imageKey: String? = null,
    // Since server app 1.10.0: who added and who ticked the item. A guest has a name and no user
    // id; public responses never carry user ids, so the *ByGuest flags tell guests from members.
    val addedBy: String? = null,
    val addedByName: String? = null,
    val addedByGuest: Boolean = false,
    val checkedByName: String? = null,
    val checkedByGuest: Boolean = false,
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

/** Per-user list preferences (PATCH …/lists/{id}/preferences); pinning is the only one so far. */
@Serializable
data class ListPreferencesRequest(val isPinned: Boolean)

@Serializable
data class CreateItemRequest(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val areaExplicit: Boolean = false,
    // Since server app 1.7.1; older servers simply ignore it (defaults are not encoded).
    val checked: Boolean = false,
    // Public link API only, since server app 1.10.0: the guest's name, left out when null.
    val guestName: String? = null,
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
data class CheckRequest(
    val checked: Boolean,
    // Public link API only, since server app 1.10.0: the guest's name, left out when null.
    val guestName: String? = null,
)

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
data class MoveItemRequest(val targetListId: Long)

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
    /** A link share's invite code (server app 1.10.0 and later). */
    val code: String? = null,
)

@Serializable
data class CreateShareRequest(
    val sharedWith: String,
    val shareType: Int,
    val permission: Int,
)

@Serializable
data class UpdateShareRequest(val permission: Int)

/** files_sharing's sharee search; exact matches come back apart from the partial ones. */
@Serializable
data class ShareesResponse(
    val exact: ShareeMatches = ShareeMatches(),
    val users: List<ShareeDto> = emptyList(),
    val groups: List<ShareeDto> = emptyList(),
)

@Serializable
data class ShareeMatches(
    val users: List<ShareeDto> = emptyList(),
    val groups: List<ShareeDto> = emptyList(),
)

@Serializable
data class ShareeDto(
    val label: String = "",
    val value: ShareeValue = ShareeValue(),
)

@Serializable
data class ShareeValue(
    val shareType: Int = 0,
    val shareWith: String = "",
)

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

/** GET/PATCH …/settings: the user's own settings (since server app 1.9.0; listSort since 1.10.0). */
@Serializable
data class SettingsDto(
    val showImages: Boolean = false,
    val listSort: String? = null,
    /** Since server app 1.10.0: show your own name on items you added or ticked. */
    val showOwnName: Boolean = false,
)

/** Only the fields set are sent; the server leaves the rest alone. */
@Serializable
data class UpdateSettingsRequest(
    val listSort: String? = null,
    val showOwnName: Boolean? = null,
)

/** The signed-in user (GET ocs/v2.php/cloud/user): their user id, which may differ from the login. */
@Serializable
data class CurrentUserDto(val id: String = "")

/** POST …/lists/reorder: one section's lists in the order wanted. */
@Serializable
data class ReorderListsRequest(val listIds: List<Long>)
