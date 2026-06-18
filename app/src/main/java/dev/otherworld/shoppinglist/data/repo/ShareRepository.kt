package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CreateLinkRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateShareRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateLinkRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateShareRequest
import dev.otherworld.shoppinglist.domain.model.ShareModel
import dev.otherworld.shoppinglist.domain.model.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** Online-only sharing management (shares aren't cached for offline). */
@Singleton
class ShareRepository @Inject constructor(
    private val service: OcsService,
    private val credentialStore: CredentialStore,
) {
    suspend fun getShares(listId: Long): List<ShareModel> =
        service.getShares(listId).ocs.data.map { it.toModel() }

    suspend fun createShare(listId: Long, sharedWith: String, type: Int, permission: Int) {
        service.createShare(listId, CreateShareRequest(sharedWith.trim(), type, permission))
    }

    suspend fun updateSharePermission(id: Long, permission: Int) {
        service.updateShare(id, UpdateShareRequest(permission))
    }

    suspend fun removeShare(id: Long) = service.deleteShare(id)

    suspend fun createLink(listId: Long, permission: Int, password: String?) {
        service.createLink(listId, CreateLinkRequest(permission, password?.ifBlank { null }))
    }

    suspend fun updateLinkPermission(id: Long, permission: Int) {
        service.updateLink(id, UpdateLinkRequest(permission = permission))
    }

    suspend fun setLinkPassword(id: Long, password: String?) {
        service.updateLink(
            id,
            if (password.isNullOrBlank()) UpdateLinkRequest(removePassword = true)
            else UpdateLinkRequest(password = password),
        )
    }

    suspend fun removeLink(id: Long) = service.deleteLink(id)

    /** Builds the public-share URL for a link token against the connected server. */
    fun linkUrl(token: String): String {
        val server = credentialStore.current()?.server?.trimEnd('/') ?: return ""
        return "$server/index.php/apps/shopping_list/s/$token"
    }
}
