package dev.otherworld.shoppinglist.ui.settings

import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StartListChoicesTest {

    private fun list(id: Long, title: String, local: Boolean = false, shareId: Long? = null) =
        ShoppingListModel(id = id, title = title, permission = 1, isOwner = shareId == null, isLocal = local, guestShareId = shareId)

    private fun titles(lists: List<ShoppingListModel>, shareIds: Set<Long> = emptySet(), loggedIn: Boolean = true) =
        startListChoices(lists, shareIds, loggedIn, Locale.UK).map { it.title }

    @Test
    fun `every list, A to Z`() {
        assertEquals(
            listOf("apples", "Bakery", "Groceries"),
            titles(listOf(list(1, "Groceries"), list(2, "apples"), list(3, "Bakery"))),
        )
    }

    @Test
    fun `lists from share links are offered too`() {
        assertEquals(listOf("Garden", "Shop"), titles(listOf(list(1, "Shop"), list(2, "Garden", shareId = 4)), shareIds = setOf(4L)))
    }

    @Test
    fun `a link list whose share has gone isn't offered`() {
        assertEquals(listOf("Shop"), titles(listOf(list(1, "Shop"), list(2, "Garden", shareId = 4))))
    }

    @Test
    fun `logged out, an old account's lists aren't offered`() {
        assertEquals(listOf("Phone"), titles(listOf(list(1, "Account"), list(2, "Phone", local = true)), loggedIn = false))
    }
}
