package dev.otherworld.shoppinglist.data.photo

import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesResponse
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ItemImagesCaps
import dev.otherworld.shoppinglist.data.remote.dto.supportsItemImages
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemImagesCapsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun caps(raw: String) = json.decodeFromString(CapabilitiesResponse.serializer(), raw).capabilities

    @Test
    fun `a server that keeps photos says so, with its limits`() {
        val c = caps(
            """{"capabilities":{"shopping_list":{"version":"1.9.0","features":["item-images"],""" +
                """"itemImages":{"maxUploadBytes":2097152,"maxSide":1280,"thumbSide":160}}}}""",
        )
        assertTrue(c.supportsItemImages())
        assertEquals(2_097_152L, c.shoppingList?.itemImages?.maxUploadBytes)
    }

    @Test
    fun `an older server does not`() {
        assertFalse(caps("""{"capabilities":{"shopping_list":{"version":"1.10.0","features":["list-order"]}}}""").supportsItemImages())
        assertFalse(caps("""{"capabilities":{}}""").supportsItemImages())
    }

    @Test
    fun `missing limits fall back to the server's own default of 10 MB`() {
        assertEquals(10L * 1024 * 1024, ItemImagesCaps().maxUploadBytes)
    }

    @Test
    fun `reads an item's photo key, and its absence`() {
        assertEquals("abcdefabcdefabcd", json.decodeFromString(ItemDto.serializer(), """{"id":1,"listId":2,"imageKey":"abcdefabcdefabcd"}""").imageKey)
        assertNull(json.decodeFromString(ItemDto.serializer(), """{"id":1,"listId":2,"imageKey":null}""").imageKey)
        assertNull(json.decodeFromString(ItemDto.serializer(), """{"id":1,"listId":2}""").imageKey)
    }
}
