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
    /** This user's pin (per-user on the server, so it never reorders anyone else's sidebar). */
    val isPinned: Boolean = false,
    /** This user's own place in the Custom order; null until placed, and after a pin or unpin. */
    val position: Int? = null,
    /** The share link this list was opened from; null for the user's own and shared lists. */
    val guestShareId: Long? = null,
    /** Kept on this phone only: its changes are never queued, until it's uploaded to an account. */
    val isLocal: Boolean = false,
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

/** A share link opened in the app: one guest list from someone else's server. */
@Entity(
    tableName = "guest_shares",
    indices = [Index(value = ["server", "token"], unique = true)],
)
data class GuestShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val server: String,
    val token: String,
    val permission: Int,
    val passwordProtected: Boolean,
    val title: String,
    val state: String,
    val lastRefreshedAt: Long,
    /** Queued changes dropped because the link stopped working or became view only, not yet told. */
    val droppedChanges: Int,
)

object GuestShareState {
    const val OK = "ok"
    const val PASSWORD_NEEDED = "passwordNeeded"
    const val DEAD = "dead"
}

/** Maps a row on a friend's server to its local id, which is [dev.otherworld.shoppinglist.domain.guest.GuestIds.fromSeq] of [seq]. */
@Entity(
    tableName = "guest_ids",
    indices = [Index(value = ["shareId", "kind", "remoteId"], unique = true)],
)
data class GuestIdEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val shareId: Long,
    val kind: String,
    /** 0 for the list itself, which the public API never numbers. */
    val remoteId: Long,
)

object GuestIdKind {
    const val LIST = "list"
    const val ITEM = "item"
    const val AREA = "area"
}

data class GuestListRef(val id: Long, val guestShareId: Long)

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
