package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.updateItemBody
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit

/**
 * What actually goes over the wire when an item is edited, through the app's own service and
 * JSON converter. The server only changes a field that is in the request, so "no area" has to
 * be there as a null, and a field left alone must not be there at all.
 */
class ItemUpdateRequestTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private var sentPath = ""
    private var sentBody = ""

    private val service: OcsService = run {
        val reply = """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":{"id":3,"listId":7,"name":"Milk"}}}"""
        val capture = Interceptor { chain ->
            val request = chain.request()
            sentPath = request.url.encodedPath
            sentBody = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType()))
                .build()
        }
        Retrofit.Builder()
            .baseUrl("https://example.invalid/")
            .client(OkHttpClient.Builder().addInterceptor(capture).build())
            .addConverterFactory(JsonConverterFactory(json))
            .build()
            .create(OcsService::class.java)
    }

    @Test
    fun `taking an item out of its area sends the area as null`() = runBlocking {
        service.updateItem(7, 3, updateItemBody(json, UpdateItemRequest(areaExplicit = true), clearArea = true))

        assertEquals("/ocs/v2.php/apps/shopping_list/api/v1/lists/7/items/3", sentPath)
        assertEquals("""{"areaExplicit":true,"shopAreaId":null}""", sentBody)
    }

    @Test
    fun `no area can go along with a new name and amount`() = runBlocking {
        val request = UpdateItemRequest(name = "Oat milk", quantity = "2", areaExplicit = true)
        service.updateItem(7, 3, updateItemBody(json, request, clearArea = true))

        assertEquals("""{"name":"Oat milk","quantity":"2","areaExplicit":true,"shopAreaId":null}""", sentBody)
    }

    @Test
    fun `an edit that leaves the area alone does not send it`() = runBlocking {
        service.updateItem(7, 3, updateItemBody(json, UpdateItemRequest(name = "Oat milk")))

        assertEquals("""{"name":"Oat milk"}""", sentBody)
    }

    @Test
    fun `a picked area is sent as its id`() = runBlocking {
        service.updateItem(7, 3, updateItemBody(json, UpdateItemRequest(shopAreaId = 12, areaExplicit = true)))

        assertEquals("""{"shopAreaId":12,"areaExplicit":true}""", sentBody)
    }
}
