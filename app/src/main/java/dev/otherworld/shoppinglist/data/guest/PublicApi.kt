package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.remote.OcsResponse
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import java.net.URLEncoder

/**
 * The web app's public share-link API, used for lists opened from a link. Every call takes a
 * full URL from [PublicUrls]: each guest list can live on a different server.
 */
interface PublicApi {
    @GET
    suspend fun show(@Url url: String): OcsResponse<PublicListDto>

    @POST
    suspend fun auth(@Url url: String, @Body body: PublicAuthRequest): OcsResponse<PublicListDto>

    @GET
    suspend fun items(@Url url: String): OcsResponse<List<ItemDto>>

    @POST
    suspend fun createItem(@Url url: String, @Body body: CreateItemRequest): OcsResponse<ItemDto>

    @PUT
    suspend fun updateItem(@Url url: String, @Body body: UpdateItemRequest): OcsResponse<ItemDto>

    @PUT
    suspend fun checkItem(@Url url: String, @Body body: CheckRequest): OcsResponse<ItemDto>

    @DELETE
    suspend fun deleteItem(@Url url: String)

    @POST
    suspend fun reorder(@Url url: String, @Body body: ReorderRequest)

    @GET
    suspend fun areas(@Url url: String): OcsResponse<List<ShopAreaDto>>

    /** The server's capabilities as anyone sees them, to learn whether it has invite codes. */
    @GET
    suspend fun capabilities(@Url url: String): OcsResponse<CapabilitiesResponse>

    @GET
    suspend fun resolveCode(@Url url: String): OcsResponse<CodeLookupDto>
}

@Serializable
data class PublicListDto(
    val title: String = "",
    val permission: Int = 0,
    val passwordRequired: Boolean = false,
)

@Serializable
data class PublicAuthRequest(val password: String)

/** What an invite code stands for: the share link's token. */
@Serializable
data class CodeLookupDto(val token: String = "")

object PublicUrls {
    private fun base(server: String, token: String) =
        "${server.trimEnd('/')}/ocs/v2.php/apps/shopping_list/api/v1/public/${URLEncoder.encode(token, "UTF-8")}"

    fun show(server: String, token: String) = base(server, token)
    fun auth(server: String, token: String) = "${base(server, token)}/auth"
    fun items(server: String, token: String) = "${base(server, token)}/items"
    fun item(server: String, token: String, id: Long) = "${base(server, token)}/items/$id"
    fun check(server: String, token: String, id: Long) = "${base(server, token)}/items/$id/check"
    fun reorder(server: String, token: String) = "${base(server, token)}/items/reorder"
    fun areas(server: String, token: String) = "${base(server, token)}/areas"
    fun capabilities(server: String) = "${server.trimEnd('/')}/ocs/v2.php/cloud/capabilities"
    fun code(server: String, code: String) =
        "${server.trimEnd('/')}/ocs/v2.php/apps/shopping_list/api/v1/public/code/${URLEncoder.encode(code, "UTF-8")}"
}
