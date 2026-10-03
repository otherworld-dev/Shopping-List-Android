package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.GuestIdKind
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GuestMappingTest {

    @Test
    fun `items and areas get local ids, and items keep their area`() = runTest {
        val ids = FakeIds()
        val listId = ids.localId(4, GuestIdKind.LIST, 0)
        val areas = mapGuestAreas(listOf(ShopAreaDto(id = 30, name = "Dairy", sortOrder = 2)), 4, listId, ids)
        val items = mapGuestItems(
            listOf(ItemDto(id = 12, listId = 99, name = "Milk", shopAreaId = 30, checked = true, sortOrder = 5)),
            4, listId, ids,
        )
        assertEquals(ids.localId(4, GuestIdKind.AREA, 30), areas.single().id)
        assertEquals(listId, areas.single().listId)
        val milk = items.single()
        assertEquals(ids.localId(4, GuestIdKind.ITEM, 12), milk.id)
        assertEquals(listId, milk.listId)
        assertEquals(areas.single().id, milk.shopAreaId)
        assertEquals(true, milk.checked)
        assertEquals(5, milk.sortOrder)
    }

    @Test
    fun `an item keeps its photo key`() = runTest {
        val ids = FakeIds()
        val items = mapGuestItems(
            listOf(ItemDto(id = 12, listId = 99, imageKey = "abcdefabcdefabcd"), ItemDto(id = 13, listId = 99)),
            4, 1, ids,
        )
        assertEquals(listOf("abcdefabcdefabcd", null), items.map { it.imageKey })
    }

    @Test
    fun `the same remote row always gets the same local id`() = runTest {
        val ids = FakeIds()
        val first = mapGuestItems(listOf(ItemDto(id = 12, listId = 99)), 4, 1, ids).single().id
        val again = mapGuestItems(listOf(ItemDto(id = 12, listId = 99)), 4, 1, ids).single().id
        assertEquals(first, again)
    }
}
