package dev.otherworld.shoppinglist.ui.lists

import dev.otherworld.shoppinglist.domain.guest.GuestIds
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleListsTest {

    private fun list(id: Long, isLocal: Boolean = false, guestShareId: Long? = null) = ShoppingListModel(
        id = id, title = "List $id", permission = Permission.WRITE, isOwner = true,
        guestShareId = guestShareId, isLocal = isLocal,
    )

    private val account = list(5)
    private val unsynced = list(-6)
    private val local = list(-7, isLocal = true)
    private val guest = list(GuestIds.fromSeq(1), guestShareId = 1)

    @Test
    fun `logged in, the account's lists and the phone's lists show`() {
        assertEquals(listOf(account, unsynced, local), ownListsShown(listOf(account, unsynced, local, guest), loggedIn = true))
    }

    @Test
    fun `logged out, only the phone's lists show`() {
        assertEquals(listOf(local), ownListsShown(listOf(account, unsynced, local, guest), loggedIn = false))
    }
}
