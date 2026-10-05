package dev.otherworld.shoppinglist.domain.share

import dev.otherworld.shoppinglist.data.remote.dto.ShareeDto
import dev.otherworld.shoppinglist.data.remote.dto.ShareesResponse
import dev.otherworld.shoppinglist.domain.model.ShareType

/** A user or group the share search found. */
data class ShareeOption(
    val label: String,
    val shareWith: String,
    val type: Int,
)

/**
 * The search results the share screen offers, built the way the web app's share dialog does:
 * users then groups, exact matches first in each, without the signed-in user.
 */
fun shareeOptions(response: ShareesResponse, selfId: String): List<ShareeOption> {
    fun options(found: List<ShareeDto>, type: Int) =
        found.map { ShareeOption(it.label.ifBlank { it.value.shareWith }, it.value.shareWith, type) }

    val users = options(response.exact.users + response.users, ShareType.USER)
        .filterNot { it.shareWith.equals(selfId, ignoreCase = true) }
    val groups = options(response.exact.groups + response.groups, ShareType.GROUP)
    return (users + groups)
        .filter { it.shareWith.isNotBlank() }
        .distinctBy { it.type to it.shareWith }
}
