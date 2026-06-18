package dev.otherworld.shoppinglist.data.sync

import kotlinx.serialization.Serializable

/** JSON payloads stored in the mutation queue, one per operation type. */

@Serializable
data class ItemCreatePayload(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val areaExplicit: Boolean = false,
)

@Serializable
data class ItemUpdatePayload(
    val name: String? = null,
    val quantity: String? = null,
    val unit: String? = null,
    val shopAreaId: Long? = null,
    val sortOrder: Int? = null,
    val areaExplicit: Boolean? = null,
)

@Serializable
data class CheckPayload(val checked: Boolean)

@Serializable
data class ReorderPayload(val sortedIds: List<Long>)

@Serializable
data class TitlePayload(val title: String)

object MutationEntities {
    const val ITEM = "item"
    const val LIST = "list"
}

object MutationTypes {
    const val CREATE = "create"
    const val UPDATE = "update"
    const val CHECK = "check"
    const val DELETE = "delete"
    const val REORDER = "reorder"
    const val CLEAR_CHECKED = "clearChecked"
    const val UNCHECK_ALL = "uncheckAll"
    const val RENAME = "rename"
}
