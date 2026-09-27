package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.GuestIdKind
import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.sync.CheckPayload
import dev.otherworld.shoppinglist.data.sync.ItemCreatePayload
import dev.otherworld.shoppinglist.data.sync.ItemUpdatePayload
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.ReorderPayload
import dev.otherworld.shoppinglist.data.sync.areaForQueuedCreate
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.text.SmartInput
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GuestSendResult {
    data object Done : GuestSendResult

    /** An item made offline now exists on the friend's server, locally as [localId]. */
    data class Created(val localId: Long, val updatedAt: String?, val detectedAreaId: Long?) : GuestSendResult
}

/**
 * Sends one queued item change on a guest list through the public link API. The public API
 * only takes item changes, so anything else queued for a guest list is skipped.
 */
@Singleton
class GuestSender @Inject constructor(
    private val api: PublicApi,
    private val guestApi: GuestApi,
    private val ids: GuestIdLookup,
    private val json: Json,
    private val smartInput: SmartInput,
) {
    suspend fun send(m: MutationEntity, share: GuestShareEntity, localAreas: List<ShopAreaModel>): GuestSendResult {
        if (m.entity != MutationEntities.ITEM) return GuestSendResult.Done
        return try {
            guestApi.withUnlock(share) { dispatch(m, share, localAreas) }
        } catch (e: HttpException) {
            throw translate(e, share)
        }
    }

    private suspend fun dispatch(m: MutationEntity, share: GuestShareEntity, localAreas: List<ShopAreaModel>): GuestSendResult {
        val s = share.server
        val t = share.token
        when (m.type) {
            MutationTypes.CREATE -> {
                val p = json.decodeFromString<ItemCreatePayload>(m.payload)
                val areaId = areaForQueuedCreate(p, if (p.detectArea) areasFor(m.listId, share, localAreas) else localAreas, smartInput)
                val created = api.createItem(
                    PublicUrls.items(s, t),
                    CreateItemRequest(p.name, p.quantity, p.unit, areaId?.let { ids.remoteId(it) }),
                ).ocs.data
                // The public create ignores "checked", so a ticked paste is ticked straight after.
                if (p.checked) api.checkItem(PublicUrls.check(s, t, created.id), CheckRequest(true))
                return GuestSendResult.Created(
                    localId = ids.localId(share.id, GuestIdKind.ITEM, created.id),
                    updatedAt = created.updatedAt,
                    detectedAreaId = areaId.takeIf { p.detectArea },
                )
            }
            MutationTypes.UPDATE -> {
                val remote = ids.remoteId(m.targetId) ?: return GuestSendResult.Done
                val p = json.decodeFromString<ItemUpdatePayload>(m.payload)
                api.updateItem(
                    PublicUrls.item(s, t, remote),
                    UpdateItemRequest(name = p.name, quantity = p.quantity, unit = p.unit, shopAreaId = p.shopAreaId?.let { ids.remoteId(it) }),
                )
            }
            MutationTypes.CHECK -> {
                val remote = ids.remoteId(m.targetId) ?: return GuestSendResult.Done
                val p = json.decodeFromString<CheckPayload>(m.payload)
                api.checkItem(PublicUrls.check(s, t, remote), CheckRequest(p.checked))
            }
            MutationTypes.DELETE -> {
                val remote = ids.remoteId(m.targetId) ?: return GuestSendResult.Done
                api.deleteItem(PublicUrls.item(s, t, remote))
            }
            MutationTypes.REORDER -> {
                val p = json.decodeFromString<ReorderPayload>(m.payload)
                val remoteIds = p.sortedIds.mapNotNull { ids.remoteId(it) }
                if (remoteIds.isNotEmpty()) api.reorder(PublicUrls.reorder(s, t), ReorderRequest(remoteIds))
            }
        }
        return GuestSendResult.Done
    }

    private suspend fun areasFor(listId: Long, share: GuestShareEntity, localAreas: List<ShopAreaModel>): List<ShopAreaModel> {
        if (localAreas.isNotEmpty()) return localAreas
        val remote = api.areas(PublicUrls.areas(share.server, share.token)).ocs.data
        return mapGuestAreas(remote, share.id, listId, ids).map { it.toModel() }
    }

    /**
     * The app's own 404 means the item went, or the whole link did; asking for the list tells
     * which. Any other 404 (the app disabled, a proxy) is the server's trouble, not the link's.
     */
    private suspend fun translate(e: HttpException, share: GuestShareEntity): Exception = when (guestApi.errorOf(e)) {
        PublicError.NotFound -> if (linkGone(share)) GuestLinkDeadException(share.id) else e
        PublicError.Other(404) -> GuestServerTroubleException(share.id)
        PublicError.ReadOnly -> GuestReadOnlyException(share.id)
        PublicError.PasswordRequired -> GuestPasswordNeededException(share.id)
        else -> e
    }

    private suspend fun linkGone(share: GuestShareEntity): Boolean = try {
        guestApi.withUnlock(share) { api.show(PublicUrls.show(share.server, share.token)) }
        false
    } catch (e: HttpException) {
        guestApi.errorOf(e) == PublicError.NotFound
    }
}
