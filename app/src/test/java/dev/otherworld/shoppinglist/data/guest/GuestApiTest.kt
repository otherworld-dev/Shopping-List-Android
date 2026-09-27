package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.local.GuestShareState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val share = GuestShareEntity(
        id = 4, server = "https://example.com", token = "tok", permission = 1, passwordProtected = true,
        title = "Holiday", state = GuestShareState.OK, lastRefreshedAt = 0, droppedChanges = 0,
    )
    private val api = FakePublicApi()
    private val showUrl = PublicUrls.show(share.server, share.token)
    private val authUrl = PublicUrls.auth(share.server, share.token)

    @Test
    fun `a call that works needs no unlocking`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(mutableMapOf(4L to "pw")), json)
        guestApi.withUnlock(share) { api.show(showUrl) }
        assertEquals(listOf("show $showUrl"), api.calls)
    }

    @Test
    fun `asked for the password, it unlocks with the stored one and tries again once`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(mutableMapOf(4L to "pw")), json)
        api.failNext += "show" to httpError(403, """{"passwordRequired":true}""")
        guestApi.withUnlock(share) { api.show(showUrl) }
        assertEquals(listOf("show $showUrl", "auth $authUrl", "show $showUrl"), api.calls)
        assertEquals(PublicAuthRequest("pw"), api.bodies.single())
    }

    @Test
    fun `with no stored password the link waits for one`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(), json)
        api.failNext += "show" to httpError(403, """{"passwordRequired":true}""")
        val e = runCatching { guestApi.withUnlock(share) { api.show(showUrl) } }.exceptionOrNull()
        assertEquals(4L, (e as GuestPasswordNeededException).shareId)
    }

    @Test
    fun `a changed password makes the link wait for the new one`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(mutableMapOf(4L to "old")), json)
        api.failNext += "show" to httpError(403, """{"passwordRequired":true}""")
        api.failNext += "auth" to httpError(403, """{"message":"Invalid password"}""")
        val e = runCatching { guestApi.withUnlock(share) { api.show(showUrl) } }.exceptionOrNull()
        assertEquals(GuestPasswordNeededException::class, e!!::class)
    }

    @Test
    fun `a caller that waited while another unlocked doesn't unlock again`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(mutableMapOf(4L to "pw")), json)
        val seen = guestApi.generation(4)
        guestApi.unlock(share, seen)
        guestApi.unlock(share, seen)
        assertEquals(listOf("auth $authUrl"), api.calls)
    }

    @Test
    fun `the error is still readable after the body was read once`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(), json)
        val e = httpError(403, """{"message":"Read-only access"}""")
        assertEquals(PublicError.ReadOnly, guestApi.errorOf(e))
        assertEquals(PublicError.ReadOnly, guestApi.errorOf(e))
    }

    @Test
    fun `other failures pass straight through`() = runTest {
        val guestApi = GuestApi(api, FakePasswords(mutableMapOf(4L to "pw")), json)
        api.failNext += "show" to httpError(404, """{"message":"Not found"}""")
        val e = runCatching { guestApi.withUnlock(share) { api.show(showUrl) } }.exceptionOrNull()
        assertTrue(e is retrofit2.HttpException && e.code() == 404)
        assertEquals(listOf("show $showUrl"), api.calls)
    }
}
