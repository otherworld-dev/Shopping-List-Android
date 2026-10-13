package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.remote.OcsBody
import dev.otherworld.shoppinglist.data.remote.OcsMeta
import dev.otherworld.shoppinglist.data.remote.OcsResponse
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.CheckRequest
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** An HTTP error as the public API sends it: OCS envelope, [data] as its data. */
fun httpError(code: Int, data: String = "{}"): HttpException = HttpException(
    Response.error<Any>(
        code,
        """{"ocs":{"meta":{"status":"failure","statuscode":$code},"data":$data}}"""
            .toResponseBody("application/json".toMediaType()),
    ),
)

fun <T> ocs(data: T) = OcsResponse(OcsBody(OcsMeta(), data))

/** Records every call as "op url"; fails the next call to an op queued in [failNext]. */
class FakePublicApi : PublicApi {
    val calls = mutableListOf<String>()
    val bodies = mutableListOf<Any>()
    val failNext = ArrayDeque<Pair<String, Throwable>>()
    var list = PublicListDto(title = "Holiday", permission = 1)
    var items = listOf<ItemDto>()
    var areas = listOf<ShopAreaDto>()
    var nextItemId = 500L
    /** What the server remembers for a name added on its own; it falls back to "1". */
    var rememberedQuantity: String? = null

    /** The friend's list's default area (server app 1.12.0), given to an item sent with none. */
    var defaultAreaId: Long? = null
    var capabilities = CapabilitiesResponse()
    var codeToken = "tok"

    private fun record(op: String, url: String, body: Any? = null) {
        calls += "$op $url"
        body?.let { bodies += it }
        val next = failNext.firstOrNull()
        if (next != null && next.first == op) {
            failNext.removeFirst()
            throw next.second
        }
    }

    override suspend fun show(url: String) = record("show", url).let { ocs(list) }
    override suspend fun auth(url: String, body: PublicAuthRequest) = record("auth", url, body).let { ocs(list) }
    override suspend fun items(url: String) = record("items", url).let { ocs(items) }
    override suspend fun createItem(url: String, body: CreateItemRequest): OcsResponse<ItemDto> {
        record("createItem", url, body)
        val quantity = body.quantity ?: rememberedQuantity ?: "1"
        return ocs(ItemDto(id = nextItemId++, listId = 9, name = body.name, quantity = quantity, shopAreaId = body.shopAreaId ?: defaultAreaId, updatedAt = "2026-09-27T10:00:00+00:00"))
    }
    override suspend fun updateItem(url: String, body: JsonObject) =
        record("updateItem", url, body).let { ocs(ItemDto(id = 1, listId = 9)) }
    override suspend fun checkItem(url: String, body: CheckRequest) =
        record("checkItem", url, body).let { ocs(ItemDto(id = 1, listId = 9)) }
    override suspend fun deleteItem(url: String) = record("deleteItem", url)
    override suspend fun reorder(url: String, body: ReorderRequest) = record("reorder", url, body)
    override suspend fun areas(url: String) = record("areas", url).let { ocs(areas) }
    override suspend fun capabilities(url: String) = record("capabilities", url).let { ocs(capabilities) }
    override suspend fun resolveCode(url: String) = record("resolveCode", url).let { ocs(CodeLookupDto(codeToken)) }
}

class FakePasswords(private val stored: MutableMap<Long, String> = mutableMapOf()) : GuestPasswords {
    override fun get(shareId: Long) = stored[shareId]
    override fun put(shareId: Long, password: String) { stored[shareId] = password }
    override fun remove(shareId: Long) { stored.remove(shareId) }
}
