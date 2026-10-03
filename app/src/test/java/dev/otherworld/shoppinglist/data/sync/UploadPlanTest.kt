package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.local.ItemEntity
import dev.otherworld.shoppinglist.data.local.ListEntity
import dev.otherworld.shoppinglist.domain.model.Permission
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadPlanTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun list(pinned: Boolean = false) = ListEntity(
        id = -10, title = "Groceries", permission = Permission.WRITE, isOwner = true,
        sortOrder = -1, updatedAt = null, isPinned = pinned, isLocal = true,
    )

    private fun item(id: Long, name: String, sortOrder: Int, checked: Boolean = false) = ItemEntity(
        id = id, listId = -10, name = name, quantity = "2", unit = "kg", shopAreaId = null,
        checked = checked, checkedBy = null, sortOrder = sortOrder, updatedAt = null,
    )

    @Test
    fun `an empty list is just created`() {
        val plan = planUpload(list(), emptyList(), json)
        assertEquals(1, plan.size)
        val create = plan.single()
        assertEquals(MutationEntities.LIST, create.entity)
        assertEquals(MutationTypes.CREATE, create.type)
        assertEquals(-10L, create.targetId)
        assertEquals(-10L, create.listId)
        assertEquals("Groceries", json.decodeFromString<TitlePayload>(create.payload).title)
    }

    @Test
    fun `items are created after the list, in their order, keeping their ticks`() {
        val plan = planUpload(
            list(),
            listOf(item(-3, "milk", sortOrder = 2, checked = true), item(-2, "bread", sortOrder = 0), item(-4, "eggs", sortOrder = 1)),
            json,
        )
        assertEquals(listOf(MutationTypes.CREATE, MutationTypes.CREATE, MutationTypes.CREATE, MutationTypes.CREATE), plan.map { it.type })
        assertEquals(MutationEntities.LIST, plan[0].entity)
        val items = plan.drop(1)
        assertEquals(listOf(-2L, -4L, -3L), items.map { it.targetId })
        items.forEach {
            assertEquals(MutationEntities.ITEM, it.entity)
            assertEquals(-10L, it.listId)
        }
        val milk = json.decodeFromString<ItemCreatePayload>(items[2].payload)
        assertEquals(ItemCreatePayload("milk", "2", "kg", shopAreaId = null, areaExplicit = false, checked = true, detectArea = true), milk)
    }

    @Test
    fun `a pinned list keeps its pin`() {
        val plan = planUpload(list(pinned = true), listOf(item(-2, "bread", sortOrder = 0)), json)
        val pin = plan.last()
        assertEquals(MutationEntities.LIST, pin.entity)
        assertEquals(MutationTypes.UPDATE_PREFERENCES, pin.type)
        assertEquals(-10L, pin.targetId)
        assertEquals(true, json.decodeFromString<PinPayload>(pin.payload).isPinned)
    }
}
