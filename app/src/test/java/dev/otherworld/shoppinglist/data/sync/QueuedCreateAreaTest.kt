package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.text.SmartInput
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An item added before its list's areas reached the phone couldn't be auto-detected, and the
 * server never detects areas itself, so it would stay uncategorized for good. The queued create
 * carries a flag instead, and detection runs when it is sent.
 */
class QueuedCreateAreaTest {

    private val smartInput = SmartInput.english()
    private val dairy = ShopAreaModel(id = 2, listId = 1, name = "Dairy", sortOrder = 0, color = null, keywords = listOf("milk", "cheese"))
    private val bakery = ShopAreaModel(id = 3, listId = 1, name = "Bakery", sortOrder = 1, color = null, keywords = listOf("bread"))
    private val areas = listOf(dairy, bakery)

    @Test
    fun `detects the area for a create that was queued before the areas were known`() {
        val payload = ItemCreatePayload(name = "Milk", detectArea = true)
        assertEquals(dairy.id, areaForQueuedCreate(payload, areas, smartInput))
    }

    @Test
    fun `leaves a create alone when detection already ran when it was added`() {
        val payload = ItemCreatePayload(name = "Milk", detectArea = false)
        assertNull(areaForQueuedCreate(payload, areas, smartInput))
    }

    @Test
    fun `keeps an area the user picked`() {
        val payload = ItemCreatePayload(name = "Milk", shopAreaId = bakery.id, areaExplicit = true, detectArea = true)
        assertEquals(bakery.id, areaForQueuedCreate(payload, areas, smartInput))
    }

    @Test
    fun `stays uncategorized when no keyword matches`() {
        val payload = ItemCreatePayload(name = "Batteries", detectArea = true)
        assertNull(areaForQueuedCreate(payload, areas, smartInput))
    }

    @Test
    fun `stays uncategorized when the list has no areas`() {
        val payload = ItemCreatePayload(name = "Milk", detectArea = true)
        assertNull(areaForQueuedCreate(payload, emptyList(), smartInput))
    }

    @Test
    fun `a create queued by an older version reads as already detected`() {
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<ItemCreatePayload>("""{"name":"Milk"}""")
        assertFalse(payload.detectArea)
    }
}
