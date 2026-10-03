package dev.otherworld.shoppinglist.domain.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteTextTest {

    @Test
    fun `shows a code in two halves, however the server sent it`() {
        assertEquals("K7QM-3XPD", formatInviteCode("K7QM3XPD"))
        assertEquals("K7QM-3XPD", formatInviteCode("K7QM-3XPD"))
        assertEquals("K7QM-3XPD", formatInviteCode("k7qm3xpd"))
    }

    @Test
    fun `no code, no text`() {
        assertNull(formatInviteCode(null))
        assertNull(formatInviteCode(""))
        assertNull(formatInviteCode("nonsense"))
        assertNull(inviteText("https://cloud.example.com", null))
        assertNull(inviteText("", "K7QM3XPD"))
    }

    // Matches the web app's Copy invite button.
    @Test
    fun `drops https and keeps a port and sub-path`() {
        assertEquals("cloud.example.com/K7QM-3XPD", inviteText("https://cloud.example.com", "K7QM3XPD"))
        assertEquals("cloud.example.com/K7QM-3XPD", inviteText("https://cloud.example.com/", "K7QM3XPD"))
        assertEquals("example.com:8443/nextcloud/K7QM-3XPD", inviteText("https://example.com:8443/nextcloud", "K7QM3XPD"))
    }

    @Test
    fun `an invite reads back as the same server and code`() {
        for (server in listOf("https://cloud.example.com", "https://example.com:8443/nextcloud")) {
            assertEquals(JoinInput.Code(server, "K7QM3XPD"), parseJoinInput(inviteText(server, "K7QM-3XPD")!!))
        }
    }
}
