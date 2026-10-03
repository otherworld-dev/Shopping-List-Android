package dev.otherworld.shoppinglist.ui.join

import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.guest.CodeNotFoundException
import dev.otherworld.shoppinglist.data.guest.CodesUnsupportedException
import dev.otherworld.shoppinglist.data.guest.ShoppingListMissingException
import dev.otherworld.shoppinglist.data.guest.TooManyTriesException
import dev.otherworld.shoppinglist.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class JoinEntryMessagesTest {

    @Test
    fun `each lookup failure has its own message`() {
        assertEquals(UiText(R.string.join_entry_codes_unsupported), joinEntryErrorText(CodesUnsupportedException()))
        assertEquals(UiText(R.string.join_entry_code_not_found), joinEntryErrorText(CodeNotFoundException()))
        assertEquals(UiText(R.string.join_entry_app_missing), joinEntryErrorText(ShoppingListMissingException()))
        assertEquals(UiText(R.string.join_too_many_tries), joinEntryErrorText(TooManyTriesException()))
    }

    @Test
    fun `anything else reads as usual`() {
        assertEquals(UiText(R.string.error_offline), joinEntryErrorText(IOException("timeout")))
    }
}
