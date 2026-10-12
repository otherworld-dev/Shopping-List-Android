package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.remote.dto.defaultAreaBody
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
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit

/**
 * What actually goes over the wire when a list's default area is set (server app 1.12.0),
 * through the app's own service and JSON converter, with the server's reply stood in for.
 */
class DefaultAreaRequestTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private var sentMethod = ""
    private var sentPath = ""
    private var sentBody = ""

    private fun service(replyDefaultArea: String): OcsService {
        val reply = """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":{"id":7,"title":"Groceries","defaultShopAreaId":$replyDefaultArea}}}"""
        val capture = Interceptor { chain ->
            val request = chain.request()
            sentMethod = request.method
            sentPath = request.url.encodedPath
            sentBody = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return Retrofit.Builder()
            .baseUrl("https://example.invalid/")
            .client(OkHttpClient.Builder().addInterceptor(capture).build())
            .addConverterFactory(JsonConverterFactory(json))
            .build()
            .create(OcsService::class.java)
    }

    @Test
    fun `choosing an area puts its id to the list's default-area`() = runBlocking {
        val list = service(replyDefaultArea = "12").setDefaultArea(7, defaultAreaBody(12)).ocs.data

        assertEquals("PUT", sentMethod)
        assertEquals("/ocs/v2.php/apps/shopping_list/api/v1/lists/7/default-area", sentPath)
        assertEquals("""{"shopAreaId":12}""", sentBody)
        assertEquals(12L, list.defaultShopAreaId)
    }

    @Test
    fun `choosing none sends the field as null rather than leaving it out`() = runBlocking {
        val list = service(replyDefaultArea = "null").setDefaultArea(7, defaultAreaBody(null)).ocs.data

        assertEquals("""{"shopAreaId":null}""", sentBody)
        assertNull(list.defaultShopAreaId)
    }
}
