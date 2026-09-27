package dev.otherworld.shoppinglist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY sortOrder, title COLLATE NOCASE")
    fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getById(id: Long): ListEntity?

    @Query("SELECT id FROM lists")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<ListEntity>)

    @Update
    suspend fun update(list: ListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM lists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM lists WHERE guestShareId = :shareId")
    suspend fun getByGuestShare(shareId: Long): ListEntity?

    @Query("SELECT id, guestShareId FROM lists WHERE guestShareId IS NOT NULL")
    suspend fun guestLists(): List<GuestListRef>
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY sortOrder, id")
    fun observeByList(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE listId = :listId")
    suspend fun getByList(listId: Long): List<ItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM items WHERE listId = :listId")
    suspend fun maxSortOrder(listId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM items WHERE listId = :listId")
    suspend fun deleteByList(listId: Long)

    @Query("DELETE FROM items WHERE listId = :listId AND checked = 1")
    suspend fun deleteCheckedByList(listId: Long)

    @Query("UPDATE items SET checked = 0, checkedBy = NULL WHERE listId = :listId")
    suspend fun uncheckAllByList(listId: Long)

    @Query("UPDATE items SET listId = :newId WHERE listId = :oldId")
    suspend fun remapListId(oldId: Long, newId: Long)
}

@Dao
interface AreaDao {
    // id is a deterministic tiebreak so equal sortOrder never renders in an arbitrary order.
    @Query("SELECT * FROM areas WHERE listId = :listId ORDER BY sortOrder, id")
    fun observeByList(listId: Long): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE listId = :listId ORDER BY sortOrder, id")
    suspend fun getByList(listId: Long): List<AreaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(areas: List<AreaEntity>)

    @Query("DELETE FROM areas WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM areas WHERE listId = :listId")
    suspend fun deleteByList(listId: Long)

    @Query("UPDATE areas SET listId = :newId WHERE listId = :oldId")
    suspend fun remapListId(oldId: Long, newId: Long)
}

@Dao
interface MutationDao {
    @Query("SELECT * FROM mutations ORDER BY seq ASC")
    suspend fun all(): List<MutationEntity>

    @Query("SELECT * FROM mutations ORDER BY seq ASC LIMIT 1")
    suspend fun oldest(): MutationEntity?

    @Query("SELECT * FROM mutations WHERE seq = :seq")
    suspend fun getBySeq(seq: Long): MutationEntity?

    @Query("SELECT COUNT(*) FROM mutations")
    fun count(): Flow<Int>

    @Insert
    suspend fun insert(mutation: MutationEntity): Long

    @Update
    suspend fun update(mutation: MutationEntity)

    @Query("DELETE FROM mutations WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)

    @Query("UPDATE mutations SET targetId = :newId WHERE targetId = :oldId AND entity = :entity")
    suspend fun remapTarget(entity: String, oldId: Long, newId: Long)

    @Query("UPDATE mutations SET listId = :newId WHERE listId = :oldId")
    suspend fun remapListId(oldId: Long, newId: Long)

    @Query("SELECT * FROM mutations WHERE type = 'reorder'")
    suspend fun reorderMutations(): List<MutationEntity>

    @Query("SELECT * FROM mutations WHERE type = :type")
    suspend fun byType(type: String): List<MutationEntity>

    @Query("SELECT COUNT(*) FROM mutations WHERE type = :type")
    suspend fun countByType(type: String): Int

    /** Count of pending shop-area mutations for a list (currently only reorders). */
    @Query("SELECT COUNT(*) FROM mutations WHERE entity = 'area' AND listId = :listId")
    suspend fun pendingAreaCount(listId: Long): Int

    /** Drops any queued area reorder for a list so a newer one supersedes it. */
    @Query("DELETE FROM mutations WHERE entity = 'area' AND type = 'reorder' AND listId = :listId")
    suspend fun deleteAreaReorders(listId: Long)

    @Query("SELECT DISTINCT targetId FROM mutations WHERE entity = 'list'")
    suspend fun pendingListIds(): List<Long>

    @Query("SELECT DISTINCT targetId FROM mutations WHERE entity = 'item' AND listId = :listId")
    suspend fun pendingItemIds(listId: Long): List<Long>

    @Query("DELETE FROM mutations WHERE entity = :entity AND targetId = :id")
    suspend fun deleteByTarget(entity: String, id: Long)

    /** Drops everything queued for one list; returns how many changes that was. */
    @Query("DELETE FROM mutations WHERE listId = :listId")
    suspend fun deleteByList(listId: Long): Int
}

@Dao
interface GuestShareDao {
    @Query("SELECT * FROM guest_shares ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<GuestShareEntity>>

    @Query("SELECT COUNT(*) FROM guest_shares")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT guest_shares.* FROM guest_shares " +
            "INNER JOIN lists ON lists.guestShareId = guest_shares.id WHERE lists.id = :listId",
    )
    fun observeForList(listId: Long): Flow<GuestShareEntity?>

    @Query("SELECT * FROM guest_shares")
    suspend fun all(): List<GuestShareEntity>

    @Query("SELECT * FROM guest_shares WHERE id = :id")
    suspend fun getById(id: Long): GuestShareEntity?

    @Query("SELECT * FROM guest_shares WHERE server = :server AND token = :token")
    suspend fun find(server: String, token: String): GuestShareEntity?

    @Query(
        "SELECT guest_shares.* FROM guest_shares " +
            "INNER JOIN lists ON lists.guestShareId = guest_shares.id WHERE lists.id = :listId",
    )
    suspend fun forList(listId: Long): GuestShareEntity?

    @Insert
    suspend fun insert(share: GuestShareEntity): Long

    @Update
    suspend fun update(share: GuestShareEntity)

    @Query("DELETE FROM guest_shares WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GuestIdDao {
    @Query("SELECT * FROM guest_ids WHERE shareId = :shareId AND kind = :kind AND remoteId = :remoteId")
    suspend fun find(shareId: Long, kind: String, remoteId: Long): GuestIdEntity?

    @Query("SELECT * FROM guest_ids WHERE seq = :seq")
    suspend fun bySeq(seq: Long): GuestIdEntity?

    @Insert
    suspend fun insert(row: GuestIdEntity): Long

    @Query("DELETE FROM guest_ids WHERE shareId = :shareId")
    suspend fun deleteByShare(shareId: Long)
}
