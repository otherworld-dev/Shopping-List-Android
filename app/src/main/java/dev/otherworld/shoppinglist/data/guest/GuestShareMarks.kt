package dev.otherworld.shoppinglist.data.guest

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.GuestShareState
import dev.otherworld.shoppinglist.domain.model.Permission
import javax.inject.Inject
import javax.inject.Singleton

/** Records what happened to a share link, shared by the queue and the refresh. */
@Singleton
class GuestShareMarks @Inject constructor(private val db: AppDatabase) {
    private val shareDao = db.guestShareDao()
    private val listDao = db.listDao()
    private val mutationDao = db.mutationDao()

    /** The link stopped working: whatever was still queued for it can never be sent. */
    suspend fun dead(shareId: Long) {
        db.withTransaction {
            val dropped = listDao.getByGuestShare(shareId)?.let { mutationDao.deleteByList(it.id) } ?: 0
            shareDao.getById(shareId)?.let {
                shareDao.update(it.copy(state = GuestShareState.DEAD, droppedChanges = it.droppedChanges + dropped))
            }
        }
    }

    /** The owner made the link view only: queued edits can never be sent. */
    suspend fun readOnly(shareId: Long) {
        db.withTransaction {
            val list = listDao.getByGuestShare(shareId)
            val dropped = list?.let { mutationDao.deleteByList(it.id) } ?: 0
            list?.let { listDao.update(it.copy(permission = Permission.READ)) }
            shareDao.getById(shareId)?.let {
                shareDao.update(it.copy(permission = Permission.READ, droppedChanges = it.droppedChanges + dropped))
            }
        }
    }

    suspend fun passwordNeeded(shareId: Long) {
        shareDao.setState(shareId, GuestShareState.PASSWORD_NEEDED)
    }
}
