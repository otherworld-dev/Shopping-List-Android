package dev.otherworld.shoppinglist.data.guest

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.GuestIdEntity
import dev.otherworld.shoppinglist.domain.guest.GuestIds
import javax.inject.Inject
import javax.inject.Singleton

/** Local ids for rows on a friend's server, and the way back. */
interface GuestIdLookup {
    /** The local id for a row on the friend's server, made the first time it's seen. */
    suspend fun localId(shareId: Long, kind: String, remoteId: Long): Long

    /** The friend's id for a local one, or null when it has none (made offline and not yet sent). */
    suspend fun remoteId(localId: Long): Long?
}

@Singleton
class GuestIdStore @Inject constructor(private val db: AppDatabase) : GuestIdLookup {
    private val dao = db.guestIdDao()

    override suspend fun localId(shareId: Long, kind: String, remoteId: Long): Long = db.withTransaction {
        val seq = dao.find(shareId, kind, remoteId)?.seq
            ?: dao.insert(GuestIdEntity(shareId = shareId, kind = kind, remoteId = remoteId))
        GuestIds.fromSeq(seq)
    }

    override suspend fun remoteId(localId: Long): Long? {
        if (!GuestIds.isGuest(localId)) return null
        return dao.bySeq(localId - GuestIds.BASE)?.remoteId
    }
}
