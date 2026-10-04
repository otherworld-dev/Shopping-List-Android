package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.local.toEntity
import dev.otherworld.shoppinglist.data.local.toModel
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.domain.guest.Attributed
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

// Who added and ticked an item (server app 1.10.0 and later) comes through to the screen.
class ItemNamesTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `an item keeps who added and ticked it, all the way to the model`() {
        val dto = json.decodeFromString(
            ItemDto.serializer(),
            """{"id":1,"listId":2,"name":"Milk","checked":true,"addedBy":"adam","addedByName":"Adam","addedByGuest":false,
               "checkedBy":null,"checkedByName":"Anna","checkedByGuest":true}""",
        )
        assertEquals(
            Attributed(addedBy = "adam", addedByName = "Adam", addedByGuest = false, checkedBy = null, checkedByName = "Anna", checkedByGuest = true),
            dto.toEntity().toModel().attributed,
        )
    }

    @Test
    fun `an item from an older server simply has no names`() {
        val dto = json.decodeFromString(ItemDto.serializer(), """{"id":1,"listId":2,"name":"Milk"}""")
        assertEquals(
            Attributed(addedBy = null, addedByName = null, addedByGuest = false, checkedBy = null, checkedByName = null, checkedByGuest = false),
            dto.toEntity().toModel().attributed,
        )
    }
}
