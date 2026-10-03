package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.remote.dto.supportsInviteCodes
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an invite code into the share link it stands for. Goes through the guest client like
 * every other share-link call, so a self-signed server gets the usual certificate check and the
 * token never reaches the app's log.
 */
@Singleton
class InviteCodes @Inject constructor(
    private val api: PublicApi,
    private val json: Json,
) {
    /**
     * Asks [server] whether it has invite codes, then what [code] (8 characters, no hyphen) is.
     * The capability check comes first so an older server says so, rather than a 404 that would
     * read as a wrong code.
     */
    suspend fun resolve(server: String, code: String): ShareLink {
        val caps = call { api.capabilities(PublicUrls.capabilities(server)) }.ocs.data.capabilities
        if (!caps.supportsInviteCodes()) throw CodesUnsupportedException()
        val token = call { api.resolveCode(PublicUrls.code(server, code)) }.ocs.data.token
        if (token.isBlank()) throw CodeNotFoundException()
        return ShareLink(server, token)
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw when (classifyCodeError(e.code(), e.response()?.errorBody()?.string(), json)) {
            CodeError.NotFound -> CodeNotFoundException()
            CodeError.AppMissing -> ShoppingListMissingException()
            CodeError.TooManyTries -> TooManyTriesException()
            is CodeError.Other -> e
        }
    }
}
