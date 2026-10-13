package dev.otherworld.shoppinglist.ui.areas

import dev.otherworld.shoppinglist.data.remote.dto.defaultAreaBody
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choosing a list's default area from the phone (server app 1.12.0 and later). */
class DefaultAreaTest {

    // The app's own settings: null fields of a request class are left out.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun list(
        permission: Int = Permission.WRITE,
        guestShareId: Long? = null,
        isLocal: Boolean = false,
    ) = ShoppingListModel(
        id = 1, title = "Groceries", permission = permission, isOwner = false,
        guestShareId = guestShareId, isLocal = isLocal,
    )

    @Test
    fun `an area is sent by its id`() {
        assertEquals("""{"shopAreaId":12}""", json.encodeToString(JsonObject.serializer(), defaultAreaBody(12)))
    }

    @Test
    fun `none is sent as null, not left out`() {
        // The server refuses a request without the field, so "none" can't be told from a slip.
        assertEquals("""{"shopAreaId":null}""", json.encodeToString(JsonObject.serializer(), defaultAreaBody(null)))
    }

    @Test
    fun `a list the user can edit on a server with the feature can have one chosen`() {
        assertTrue(canSetDefaultArea(supported = true, list = list()))
    }

    @Test
    fun `not on a server from before default areas`() {
        assertFalse(canSetDefaultArea(supported = false, list = list()))
    }

    @Test
    fun `not on a list the user can only view`() {
        assertFalse(canSetDefaultArea(supported = true, list = list(permission = Permission.READ)))
    }

    @Test
    fun `not on a list opened from a link, which belongs to someone else's server`() {
        assertFalse(canSetDefaultArea(supported = true, list = list(guestShareId = 4)))
    }

    @Test
    fun `not on a list kept on the phone, which has no areas`() {
        assertFalse(canSetDefaultArea(supported = true, list = list(isLocal = true)))
    }

    @Test
    fun `not before the list has loaded`() {
        assertFalse(canSetDefaultArea(supported = true, list = null))
    }
}
