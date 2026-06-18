package dev.otherworld.shoppinglist.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room cache + optimistic store. Synced rows use the server's positive id as primary key;
 * items/lists created while offline use a temporary negative id until the sync engine swaps
 * in the real id (mirroring the web app's negative-temp-id scheme).
 */
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val permission: Int,
    val isOwner: Boolean,
    val sortOrder: Int,
    val updatedAt: String?,
)

@Entity(
    tableName = "items",
    indices = [Index("listId")],
)
data class ItemEntity(
    @PrimaryKey val id: Long,
    val listId: Long,
    val name: String,
    val quantity: String?,
    val unit: String?,
    val shopAreaId: Long?,
    val checked: Boolean,
    val checkedBy: String?,
    val sortOrder: Int,
    val updatedAt: String?,
)

@Entity(
    tableName = "areas",
    indices = [Index("listId")],
)
data class AreaEntity(
    @PrimaryKey val id: Long,
    val listId: Long,
    val name: String,
    val sortOrder: Int,
    val color: String?,
    val keywords: List<String>,
)

/** FIFO queue of mutations awaiting sync to the server. */
@Entity(tableName = "mutations")
data class MutationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entity: String,   // "item" | "list"
    val type: String,     // create | update | check | delete | reorder | clearChecked | uncheckAll | rename
    val targetId: Long,   // item id (item ops) or list id (list ops); temp or real
    val listId: Long,     // owning list id (for item ops); == targetId for list ops
    val payload: String,  // JSON, op-specific
    val attempts: Int = 0,
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(String.serializer()), value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
