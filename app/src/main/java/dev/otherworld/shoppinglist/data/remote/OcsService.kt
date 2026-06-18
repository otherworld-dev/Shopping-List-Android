package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CopyAreasRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateAreaRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateLinkRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateListRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateShareRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateTagRequest
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ListDto
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.ShareDto
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import dev.otherworld.shoppinglist.data.remote.dto.TagDto
import dev.otherworld.shoppinglist.data.remote.dto.UpdateAreaRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateLinkRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateListRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateShareRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the Shopping List OCS API. Paths are relative to a placeholder base
 * URL; [OcsAuthInterceptor] rewrites the host to the connected server at request time.
 * Endpoints that return 204/empty bodies declare a [Unit] return type, which Retrofit's
 * built-in converter handles without invoking the JSON converter.
 */
interface OcsService {

    // ---- Lists ----

    @GET("ocs/v2.php/apps/shopping_list/api/v1/lists")
    suspend fun getLists(): OcsResponse<List<ListDto>>

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists")
    suspend fun createList(@Body body: CreateListRequest): OcsResponse<ListDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/lists/{id}")
    suspend fun updateList(@Path("id") id: Long, @Body body: UpdateListRequest): OcsResponse<ListDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/lists/{id}")
    suspend fun deleteList(@Path("id") id: Long)

    // ---- Items ----

    @GET("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items")
    suspend fun getItems(@Path("listId") listId: Long): OcsResponse<List<ItemDto>>

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items")
    suspend fun createItem(
        @Path("listId") listId: Long,
        @Body body: CreateItemRequest,
    ): OcsResponse<ItemDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/{id}")
    suspend fun updateItem(
        @Path("listId") listId: Long,
        @Path("id") id: Long,
        @Body body: UpdateItemRequest,
    ): OcsResponse<ItemDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/{id}/check")
    suspend fun checkItem(
        @Path("listId") listId: Long,
        @Path("id") id: Long,
        @Body body: CheckRequest,
    ): OcsResponse<ItemDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/{id}")
    suspend fun deleteItem(@Path("listId") listId: Long, @Path("id") id: Long)

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/checked")
    suspend fun clearChecked(@Path("listId") listId: Long)

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/uncheck-all")
    suspend fun uncheckAll(@Path("listId") listId: Long)

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/items/reorder")
    suspend fun reorder(@Path("listId") listId: Long, @Body body: ReorderRequest)

    // ---- Shop areas ----

    @GET("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/areas")
    suspend fun getAreas(@Path("listId") listId: Long): OcsResponse<List<ShopAreaDto>>

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/areas")
    suspend fun createArea(
        @Path("listId") listId: Long,
        @Body body: CreateAreaRequest,
    ): OcsResponse<ShopAreaDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/areas/{id}")
    suspend fun updateArea(
        @Path("listId") listId: Long,
        @Path("id") id: Long,
        @Body body: UpdateAreaRequest,
    ): OcsResponse<ShopAreaDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/areas/{id}")
    suspend fun deleteArea(@Path("listId") listId: Long, @Path("id") id: Long)

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/areas/copy")
    suspend fun copyAreas(
        @Path("listId") listId: Long,
        @Body body: CopyAreasRequest,
    ): OcsResponse<List<ShopAreaDto>>

    // ---- Shares ----

    @GET("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/shares")
    suspend fun getShares(@Path("listId") listId: Long): OcsResponse<List<ShareDto>>

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/shares")
    suspend fun createShare(
        @Path("listId") listId: Long,
        @Body body: CreateShareRequest,
    ): OcsResponse<ShareDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/shares/{id}")
    suspend fun updateShare(@Path("id") id: Long, @Body body: UpdateShareRequest): OcsResponse<ShareDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/shares/{id}")
    suspend fun deleteShare(@Path("id") id: Long)

    @POST("ocs/v2.php/apps/shopping_list/api/v1/lists/{listId}/shares/link")
    suspend fun createLink(
        @Path("listId") listId: Long,
        @Body body: CreateLinkRequest,
    ): OcsResponse<ShareDto>

    @PUT("ocs/v2.php/apps/shopping_list/api/v1/shares/{id}/link")
    suspend fun updateLink(@Path("id") id: Long, @Body body: UpdateLinkRequest): OcsResponse<ShareDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/shares/{id}/link")
    suspend fun deleteLink(@Path("id") id: Long)

    // ---- Tags (global per-user; backend has no item-tag assignment endpoint) ----

    @GET("ocs/v2.php/apps/shopping_list/api/v1/tags")
    suspend fun getTags(): OcsResponse<List<TagDto>>

    @POST("ocs/v2.php/apps/shopping_list/api/v1/tags")
    suspend fun createTag(@Body body: CreateTagRequest): OcsResponse<TagDto>

    @DELETE("ocs/v2.php/apps/shopping_list/api/v1/tags/{id}")
    suspend fun deleteTag(@Path("id") id: Long)

    // ---- Server capabilities (for notify_push discovery) ----

    @GET("ocs/v2.php/cloud/capabilities")
    suspend fun capabilities(): OcsResponse<CapabilitiesResponse>
}
