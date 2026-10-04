package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.CurrentUserDto
import dev.otherworld.shoppinglist.data.remote.dto.SettingsDto
import dev.otherworld.shoppinglist.data.remote.dto.UpdateSettingsRequest
import dev.otherworld.shoppinglist.data.remote.dto.supportsGuestNames
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// What the app reads and writes for names on items (server app 1.10.0 and later).
class NameSettingsApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun caps(raw: String) = json.decodeFromString(CapabilitiesResponse.serializer(), raw).capabilities

    @Test
    fun `a server with guest names says so in its features, anonymously too`() {
        assertTrue(caps("""{"capabilities":{"shopping_list":{"features":["invite-codes","guest-names"]}}}""").supportsGuestNames())
        assertFalse(caps("""{"capabilities":{"shopping_list":{"features":["invite-codes"]}}}""").supportsGuestNames())
        assertFalse(caps("""{"capabilities":{}}""").supportsGuestNames())
    }

    @Test
    fun `the show-my-name setting is off unless the server says otherwise`() {
        assertTrue(json.decodeFromString(SettingsDto.serializer(), """{"showOwnName":true}""").showOwnName)
        assertFalse(json.decodeFromString(SettingsDto.serializer(), """{"listSort":"alpha"}""").showOwnName)
    }

    // A PATCH sends only what it changes, so turning names on never resets the list sort.
    @Test
    fun `changing the show-my-name setting sends only that`() {
        assertEquals("""{"showOwnName":true}""", json.encodeToString(UpdateSettingsRequest.serializer(), UpdateSettingsRequest(showOwnName = true)))
        assertEquals("""{"listSort":"alpha"}""", json.encodeToString(UpdateSettingsRequest.serializer(), UpdateSettingsRequest(listSort = "alpha")))
    }

    @Test
    fun `the user id comes from cloud user`() {
        assertEquals("adam", json.decodeFromString(CurrentUserDto.serializer(), """{"id":"adam","displayname":"Adam Morgan"}""").id)
    }
}
