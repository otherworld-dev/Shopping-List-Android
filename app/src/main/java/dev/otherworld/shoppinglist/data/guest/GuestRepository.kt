package dev.otherworld.shoppinglist.data.guest

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.GuestIdKind
import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.local.GuestShareState
import dev.otherworld.shoppinglist.data.local.ListEntity
import dev.otherworld.shoppinglist.data.remote.dto.supportsGuestNames
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import dev.otherworld.shoppinglist.domain.model.Permission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A guest list's rows the server no longer has. Every row on a guest list is a guest id or the
 * temp id of an item added offline; either goes once nothing for it is queued, so an offline add
 * whose create was dropped (the link went view only) doesn't linger as an item nobody else sees.
 */
internal fun guestItemsGone(localIds: List<Long>, serverIds: Set<Long>, pending: Set<Long>): List<Long> =
    localIds.filter { it !in serverIds && it !in pending }

/** What a share link holds, before it's opened. */
data class LinkPreview(
    val title: String,
    val permission: Int,
    val passwordRequired: Boolean,
    val joined: Boolean,
    /** The link's server keeps guests' names (1.10.0 or later), so asking for one is worth it. */
    val guestNames: Boolean = false,
)

/** Lists opened from share links: opening, refreshing and leaving them. */
@Singleton
class GuestRepository @Inject constructor(
    private val api: PublicApi,
    private val guestApi: GuestApi,
    private val db: AppDatabase,
    private val ids: GuestIdLookup,
    private val passwords: GuestPasswords,
    private val marks: GuestShareMarks,
    private val sync: SyncEngine,
) {
    private val shareDao = db.guestShareDao()
    private val guestIdDao = db.guestIdDao()
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()
    private val areaDao = db.areaDao()
    private val mutationDao = db.mutationDao()

    fun observeShares(): Flow<List<GuestShareEntity>> = shareDao.observeAll()

    fun observeHasShares(): Flow<Boolean> = shareDao.observeCount().map { it > 0 }

    fun observeShareForList(listId: Long): Flow<GuestShareEntity?> = shareDao.observeForList(listId)

    suspend fun preview(link: ShareLink): LinkPreview {
        val existing = shareDao.find(link.server, link.token)
        val url = PublicUrls.show(link.server, link.token)
        return try {
            val dto = (if (existing != null) guestApi.withUnlock(existing) { api.show(url) } else api.show(url)).ocs.data
            LinkPreview(dto.title, dto.permission, passwordRequired = false, joined = existing != null, guestNames = keepsGuestNames(link.server))
        } catch (e: GuestPasswordNeededException) {
            LinkPreview(existing?.title.orEmpty(), existing?.permission ?: Permission.READ, passwordRequired = true, joined = true, guestNames = keepsGuestNames(link.server))
        } catch (e: HttpException) {
            when (val error = guestApi.errorOf(e)) {
                PublicError.PasswordRequired -> LinkPreview("", Permission.READ, passwordRequired = true, joined = existing != null, guestNames = keepsGuestNames(link.server))
                PublicError.NotFound -> throw LinkNotFoundException()
                is PublicError.Other -> if (error.code == 404) throw LinkNotFoundException() else throw e
                else -> throw e
            }
        }
    }

    /**
     * Whether [server] keeps guests' names, from its capabilities as anyone sees them. Asked only
     * once the link itself has answered, so a certificate prompt is never this call's; any
     * failure just means no name is asked for.
     */
    private suspend fun keepsGuestNames(server: String): Boolean = try {
        api.capabilities(PublicUrls.capabilities(server)).ocs.data.capabilities.supportsGuestNames()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /**
     * Unlocks the link if [password] is given, saves it and its list, and fetches it. Returns the
     * list's id. With no [password] on a link already held, re-fetches through the stored
     * unlock (Task 6's auto-open path) rather than a bare `show` — the server's unlock session
     * may have expired, and a fresh [GuestPasswordNeededException] out of that only means the
     * stored password no longer works, not that the link is gone.
     */
    suspend fun join(link: ShareLink, password: String?): Long {
        val existingForUnlock = if (password == null) shareDao.find(link.server, link.token) else null
        val dto = try {
            when {
                password != null -> api.auth(PublicUrls.auth(link.server, link.token), PublicAuthRequest(password)).ocs.data
                existingForUnlock != null -> guestApi.withUnlock(existingForUnlock) { api.show(PublicUrls.show(link.server, link.token)) }.ocs.data
                else -> api.show(PublicUrls.show(link.server, link.token)).ocs.data
            }
        } catch (e: HttpException) {
            throw when (val error = guestApi.errorOf(e)) {
                PublicError.WrongPassword -> WrongPasswordException()
                PublicError.PasswordRequired -> GuestPasswordNeededException(existingForUnlock?.id ?: 0)
                PublicError.NotFound -> LinkNotFoundException()
                is PublicError.Other -> if (error.code == 404) LinkNotFoundException() else e
                else -> e
            }
        }
        val (shareId, listId) = db.withTransaction {
            val existing = shareDao.find(link.server, link.token)
            val shareId = if (existing == null) {
                shareDao.insert(
                    GuestShareEntity(
                        server = link.server, token = link.token, permission = dto.permission,
                        passwordProtected = password != null, title = dto.title, state = GuestShareState.OK,
                        lastRefreshedAt = System.currentTimeMillis(), droppedChanges = 0,
                    ),
                )
            } else {
                shareDao.update(
                    existing.copy(
                        permission = dto.permission, title = dto.title, state = GuestShareState.OK,
                        passwordProtected = existing.passwordProtected || password != null,
                    ),
                )
                existing.id
            }
            val listId = ids.localId(shareId, GuestIdKind.LIST, 0)
            val row = listDao.getById(listId) ?: ListEntity(
                id = listId, title = dto.title, permission = dto.permission, isOwner = false,
                sortOrder = 0, updatedAt = null, guestShareId = shareId,
            )
            listDao.upsert(row.copy(title = dto.title, permission = dto.permission))
            shareId to listId
        }
        if (password != null) passwords.put(shareId, password)
        try {
            refreshItems(listId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The list is saved; its items arrive on the next refresh.
        }
        sync.requestSync()
        return listId
    }

    /** Title and permission of every working link, each at most once a minute unless [force]. */
    suspend fun refreshShares(force: Boolean) {
        val now = System.currentTimeMillis()
        for (share in shareDao.all()) {
            if (share.state == GuestShareState.DEAD) continue
            if (!force && now - share.lastRefreshedAt < SHARE_REFRESH_MS) continue
            // Stamped before the call, not just on success: a share that keeps failing (a stale
            // password, a dead server) must still only be tried once a minute, or a frequent
            // poller re-attempts it every time — hammering a 5-a-minute unlock limit or sitting
            // through a timeout on every poll.
            shareDao.markRefreshed(share.id, now)
            try {
                val url = PublicUrls.show(share.server, share.token)
                // Waiting for a new password: a plain show still finds a dead link, without an
                // unlock attempt that would only fail again.
                val dto = if (share.state == GuestShareState.PASSWORD_NEEDED) {
                    api.show(url).ocs.data
                } else {
                    guestApi.withUnlock(share) { api.show(url) }.ocs.data
                }
                db.withTransaction {
                    shareDao.getById(share.id)?.let {
                        shareDao.update(it.copy(title = dto.title, permission = dto.permission, state = GuestShareState.OK, lastRefreshedAt = now))
                    }
                    listDao.getByGuestShare(share.id)?.let { listDao.update(it.copy(title = dto.title, permission = dto.permission)) }
                }
            } catch (e: GuestPasswordNeededException) {
                marks.passwordNeeded(share.id)
            } catch (e: HttpException) {
                when (guestApi.errorOf(e)) {
                    // A retry after a successful unlock can still meet this 403: treat it the
                    // same as GuestPasswordNeededException rather than letting it bubble up.
                    PublicError.PasswordRequired -> marks.passwordNeeded(share.id)
                    PublicError.NotFound -> marks.dead(share.id)
                    else -> Unit
                }
            } catch (e: IOException) {
                // Offline, or that server is down: the next refresh tries again.
            }
        }
        sync.requestSync()
    }

    /** A guest list's items and areas, reconciled into Room the way ItemRepository.refresh does. */
    suspend fun refreshItems(listId: Long) {
        val share = shareDao.forList(listId) ?: return
        if (share.state != GuestShareState.OK) return
        try {
            val areas = guestApi.withUnlock(share) { api.areas(PublicUrls.areas(share.server, share.token)) }.ocs.data
            val items = guestApi.withUnlock(share) { api.items(PublicUrls.items(share.server, share.token)) }.ocs.data
            db.withTransaction {
                // Leave() may have removed this share while the fetch above was in flight. Bail
                // out inside the same transaction that would otherwise write the mapping, so
                // neither the resurrected area/item rows nor their guest_ids ever land.
                if (shareDao.getById(share.id) == null) return@withTransaction
                val areaRows = mapGuestAreas(areas, share.id, listId, ids)
                val itemRows = mapGuestItems(items, share.id, listId, ids)
                areaDao.deleteByList(listId)
                areaDao.upsertAll(areaRows)
                val pending = mutationDao.pendingItemIds(listId).toSet()
                val serverIds = itemRows.map { it.id }.toSet()
                itemDao.deleteByIds(guestItemsGone(itemDao.getByList(listId).map { it.id }, serverIds, pending))
                itemRows.forEach { if (it.id !in pending) itemDao.upsert(it) }
            }
        } catch (e: GuestPasswordNeededException) {
            marks.passwordNeeded(share.id)
            return
        } catch (e: HttpException) {
            when (guestApi.errorOf(e)) {
                // Same as above: a retry after unlocking can still get a password-required 403.
                PublicError.PasswordRequired -> {
                    marks.passwordNeeded(share.id)
                    return
                }
                PublicError.NotFound -> {
                    marks.dead(share.id)
                    return
                }
                else -> throw e
            }
        }
        sync.requestSync()
    }

    suspend fun leave(shareId: Long) {
        db.withTransaction {
            listDao.getByGuestShare(shareId)?.let { list ->
                mutationDao.deleteByList(list.id)
                itemDao.deleteByList(list.id)
                areaDao.deleteByList(list.id)
                listDao.deleteById(list.id)
            }
            guestIdDao.deleteByShare(shareId)
            shareDao.deleteById(shareId)
        }
        passwords.remove(shareId)
    }

    suspend fun clearDropped(shareId: Long) {
        shareDao.clearDropped(shareId)
    }

    /** The share link a guest list was opened from, to open it again (for a new password). */
    suspend fun linkFor(listId: Long): String? = shareDao.forList(listId)?.let { ShareLink(it.server, it.token).url }

    private companion object {
        const val SHARE_REFRESH_MS = 60_000L
    }
}
