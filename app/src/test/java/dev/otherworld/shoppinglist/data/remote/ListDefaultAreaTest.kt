package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.dto.CapabilitiesBlock
import dev.otherworld.shoppinglist.data.remote.dto.ListDto
import dev.otherworld.shoppinglist.data.remote.dto.ShoppingListCaps
import dev.otherworld.shoppinglist.data.remote.dto.supportsDefaultArea
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// A list's default area (server app 1.12.0 and later) comes through to the screen.
class ListDefaultAreaTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `a list keeps its default area, all the way to the model`() {
        val dto = json.decodeFromString(ListDto.serializer(), """{"id":3,"title":"Groceries","defaultShopAreaId":12}""")
        assertEquals(12L, dto.toEntity(sortOrder = 0).toModel().defaultAreaId)
    }

    @Test
    fun `a list with no default area has none`() {
        val dto = json.decodeFromString(ListDto.serializer(), """{"id":3,"title":"Groceries","defaultShopAreaId":null}""")
        assertNull(dto.toEntity(sortOrder = 0).toModel().defaultAreaId)
    }

    @Test
    fun `a list from an older server simply has none`() {
        val dto = json.decodeFromString(ListDto.serializer(), """{"id":3,"title":"Groceries"}""")
        assertNull(dto.toEntity(sortOrder = 0).toModel().defaultAreaId)
    }

    @Test
    fun `a server that lists the feature can keep default areas`() {
        val caps = CapabilitiesBlock(shoppingList = ShoppingListCaps(features = listOf("guest-names", "default-area")))
        assertTrue(caps.supportsDefaultArea())
    }

    @Test
    fun `an older server, or one without the app, can't`() {
        assertFalse(CapabilitiesBlock(shoppingList = ShoppingListCaps(features = listOf("guest-names"))).supportsDefaultArea())
        assertFalse(CapabilitiesBlock().supportsDefaultArea())
    }
}
