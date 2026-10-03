package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesBlock
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.ShoppingListCaps
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class InviteCodesTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val server = "https://cloud.example.com"
    private val api = FakePublicApi().apply {
        capabilities = CapabilitiesResponse(CapabilitiesBlock(shoppingList = ShoppingListCaps(features = listOf("invite-codes"))))
        codeToken = "abc123"
    }
    private val codes = InviteCodes(api, json)

    private suspend fun failure(): Throwable? = runCatching { codes.resolve(server, "K7QM3XPD") }.exceptionOrNull()

    @Test
    fun `a code turns into its link on the same server`() = runTest {
        assertEquals(ShareLink(server, "abc123"), codes.resolve(server, "K7QM3XPD"))
        assertEquals(
            listOf("capabilities ${PublicUrls.capabilities(server)}", "resolveCode ${PublicUrls.code(server, "K7QM3XPD")}"),
            api.calls,
        )
    }

    @Test
    fun `the urls are the OCS ones`() {
        assertEquals("https://cloud.example.com/ocs/v2.php/cloud/capabilities", PublicUrls.capabilities("https://cloud.example.com/"))
        assertEquals(
            "https://example.com:8443/nc/ocs/v2.php/apps/shopping_list/api/v1/public/code/K7QM3XPD",
            PublicUrls.code("https://example.com:8443/nc", "K7QM3XPD"),
        )
    }

    // Servers before 1.10.0 send no shopping_list block to a caller who isn't logged in.
    @Test
    fun `a server without invite codes is never asked about the code`() = runTest {
        api.capabilities = CapabilitiesResponse()
        assertTrue(failure() is CodesUnsupportedException)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `an unknown code is not found`() = runTest {
        api.failNext += "resolveCode" to httpError(404, """{"message":"Not found"}""")
        assertTrue(failure() is CodeNotFoundException)
    }

    @Test
    fun `a blank token is not found`() = runTest {
        api.codeToken = ""
        assertTrue(failure() is CodeNotFoundException)
    }

    @Test
    fun `a bare 404 means Shopping List isn't there`() = runTest {
        api.failNext += "capabilities" to httpError(404, "[]")
        assertTrue(failure() is ShoppingListMissingException)
    }

    @Test
    fun `rate limited means wait`() = runTest {
        api.failNext += "resolveCode" to httpError(429)
        assertTrue(failure() is TooManyTriesException)
    }

    @Test
    fun `any other server error is passed on as it is`() = runTest {
        api.failNext += "resolveCode" to httpError(500)
        assertEquals(500, (failure() as HttpException).code())
    }
}
