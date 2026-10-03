package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.ListDto
import dev.otherworld.shoppinglist.data.remote.dto.supportsListOrder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListOrderApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `a server that keeps list orders says so in its capabilities`() {
        val caps = json.decodeFromString(
            CapabilitiesResponse.serializer(),
            """{"capabilities":{"shopping_list":{"version":"1.10.0","features":["item-images","list-order"]}}}""",
        )
        assertTrue(caps.capabilities.supportsListOrder())
    }

    @Test
    fun `an older server does not`() {
        val older = json.decodeFromString(
            CapabilitiesResponse.serializer(),
            """{"capabilities":{"shopping_list":{"version":"1.9.0","features":["item-images"]}}}""",
        )
        val oldest = json.decodeFromString(CapabilitiesResponse.serializer(), """{"capabilities":{}}""")
        assertFalse(older.capabilities.supportsListOrder())
        assertFalse(oldest.capabilities.supportsListOrder())
    }

    @Test
    fun `reads a list's position, and its absence`() {
        assertEquals(2, json.decodeFromString(ListDto.serializer(), """{"id":1,"position":2}""").position)
        assertNull(json.decodeFromString(ListDto.serializer(), """{"id":1}""").position)
    }

    @Test
    fun `the database upgrades one version at a time up to 5`() {
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5), AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion })
    }
}
