package dev.otherworld.shoppinglist.data.guest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PublicErrorsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun body(data: String) = """{"ocs":{"meta":{"status":"failure"},"data":$data}}"""

    @Test
    fun `tells the public API's refusals apart`() {
        assertEquals(PublicError.PasswordRequired, classifyPublicError(403, body("""{"passwordRequired":true}"""), json))
        assertEquals(PublicError.WrongPassword, classifyPublicError(403, body("""{"message":"Invalid password"}"""), json))
        assertEquals(PublicError.ReadOnly, classifyPublicError(403, body("""{"message":"Read-only access"}"""), json))
        assertEquals(PublicError.NotFound, classifyPublicError(404, body("""{"message":"Not found"}"""), json))
    }

    @Test
    fun `anything else keeps its code`() {
        assertEquals(PublicError.Other(403), classifyPublicError(403, body("""{"message":"Nope"}"""), json))
        assertEquals(PublicError.Other(500), classifyPublicError(500, "<html>oops</html>", json))
        assertEquals(PublicError.Other(429), classifyPublicError(429, null, json))
    }

    @Test
    fun `builds the public API's urls`() {
        val server = "https://example.com/nextcloud"
        val base = "https://example.com/nextcloud/ocs/v2.php/apps/shopping_list/api/v1/public/tok"
        assertEquals(base, PublicUrls.show(server, "tok"))
        assertEquals("$base/auth", PublicUrls.auth(server, "tok"))
        assertEquals("$base/items", PublicUrls.items(server, "tok"))
        assertEquals("$base/items/7", PublicUrls.item(server, "tok", 7))
        assertEquals("$base/items/7/check", PublicUrls.check(server, "tok", 7))
        assertEquals("$base/items/reorder", PublicUrls.reorder(server, "tok"))
        assertEquals("$base/areas", PublicUrls.areas(server, "tok"))
    }
}
