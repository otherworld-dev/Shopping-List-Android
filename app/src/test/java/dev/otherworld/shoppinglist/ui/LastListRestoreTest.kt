package dev.otherworld.shoppinglist.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastListRestoreTest {

    private fun restore(
        alreadyChecked: Boolean = false,
        startRoute: String = Routes.HOME,
        hasPendingLink: Boolean = false,
        enabled: Boolean = true,
        lastListId: Long? = 7L,
    ) = shouldRestoreLastList(alreadyChecked, startRoute, hasPendingLink, enabled, lastListId)

    @Test
    fun `a fresh start on the lists screen reopens the last list`() {
        assertTrue(restore())
    }

    @Test
    fun `only once per launch, so rotation doesn't open it again`() {
        assertFalse(restore(alreadyChecked = true))
    }

    @Test
    fun `not when the app starts on the login screen`() {
        assertFalse(restore(startRoute = Routes.LOGIN))
    }

    @Test
    fun `a share link being opened comes first`() {
        assertFalse(restore(hasPendingLink = true))
    }

    @Test
    fun `not when the setting is off`() {
        assertFalse(restore(enabled = false))
    }

    @Test
    fun `not before any list has been opened`() {
        assertFalse(restore(lastListId = null))
    }
}
