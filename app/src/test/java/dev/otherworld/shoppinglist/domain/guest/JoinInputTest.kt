package dev.otherworld.shoppinglist.domain.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinInputTest {

    private val token = "3f9a0c1d2e"

    // The three forms the web app's Copy invite button hands out.
    @Test
    fun `reads the web app's invites`() {
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("cloud.example.com/K7QM-3XPD"))
        assertEquals(
            JoinInput.Code("https://example.com:8443/nextcloud", "K7QM3XPD"),
            parseJoinInput("example.com:8443/nextcloud/K7QM-3XPD"),
        )
        assertEquals(JoinInput.Insecure, parseJoinInput("http://192.168.0.11:8080/K7QM-3XPD"))
    }

    @Test
    fun `a share link is still a link`() {
        assertEquals(
            JoinInput.Link(ShareLink("https://cloud.example.com", token)),
            parseJoinInput("https://cloud.example.com/index.php/apps/shopping_list/s/$token"),
        )
    }

    @Test
    fun `an http share link can't be used`() {
        assertEquals(JoinInput.Insecure, parseJoinInput("http://cloud.example.com/apps/shopping_list/s/$token"))
    }

    @Test
    fun `an invite with https in front is fine`() {
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("https://cloud.example.com/K7QM-3XPD"))
    }

    @Test
    fun `splits at the last slash and drops index php`() {
        assertEquals(
            JoinInput.Code("https://example.com/a/b", "K7QM3XPD"),
            parseJoinInput("example.com/a/b/index.php/K7QM-3XPD"),
        )
    }

    @Test
    fun `a trailing slash after the code is fine`() {
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("cloud.example.com/K7QM-3XPD/"))
    }

    // A phone keyboard or a pasted chat message can turn these into look-alikes the server won't strip.
    @Test
    fun `cleans up non-breaking spaces and unicode dashes`() {
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("cloud.example.com/K7QM\u20113XPD"))
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("cloud.example.com/K7QM\u00A03XPD"))
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("cloud.example.com/K7QM\u2014 3XPD"))
    }

    @Test
    fun `ignores case`() {
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("HTTPS://Cloud.Example.com/k7qm-3xpd"))
    }

    @Test
    fun `takes a code with or without its hyphen, or with spaces`() {
        assertEquals("K7QM3XPD", normaliseCode("K7QM-3XPD"))
        assertEquals("K7QM3XPD", normaliseCode("k7qm3xpd"))
        assertEquals("K7QM3XPD", normaliseCode(" K7QM 3XPD "))
    }

    // Checked here so a typo never costs one of the server's throttled lookups.
    @Test
    fun `refuses characters outside the code alphabet and wrong lengths`() {
        assertNull(normaliseCode("K7QM-3XP0"))
        assertNull(normaliseCode("K7QM-3XPO"))
        assertNull(normaliseCode("K7QM-3XPI"))
        assertNull(normaliseCode("K7QM-3XPL"))
        assertNull(normaliseCode("K7QM-3XP1"))
        assertNull(normaliseCode("K7QM-3XP"))
        assertNull(normaliseCode("K7QM-3XPDD"))
        assertNull(normaliseCode(""))
        assertEquals(JoinInput.Invalid, parseJoinInput("cloud.example.com/K7QM-3XP0"))
    }

    @Test
    fun `a bare code needs a server`() {
        assertEquals(JoinInput.NeedsServer, parseJoinInput("K7QM-3XPD"))
        assertEquals(JoinInput.NeedsServer, parseJoinInput("K7QM-3XPD", "   "))
        assertEquals(JoinInput.Code("https://cloud.example.com", "K7QM3XPD"), parseJoinInput("K7QM-3XPD", "cloud.example.com"))
        assertTrue(isBareCode("k7qm 3xpd"))
        assertFalse(isBareCode("cloud.example.com/K7QM-3XPD"))
        assertFalse(isBareCode("K7QM"))
    }

    @Test
    fun `takes a full server URL in the Server field`() {
        assertEquals(
            JoinInput.Code("https://cloud.example.com", "K7QM3XPD"),
            parseJoinInput("K7QM-3XPD", "https://cloud.example.com/index.php/"),
        )
        assertEquals(
            JoinInput.Code("https://example.com:8443/nextcloud", "K7QM3XPD"),
            parseJoinInput("K7QM-3XPD", "example.com:8443/nextcloud/"),
        )
    }

    @Test
    fun `an http or broken Server field is refused`() {
        assertEquals(JoinInput.Insecure, parseJoinInput("K7QM-3XPD", "http://cloud.example.com"))
        assertEquals(JoinInput.InvalidServer, parseJoinInput("K7QM-3XPD", "ftp://cloud.example.com"))
        assertEquals(JoinInput.InvalidServer, parseJoinInput("K7QM-3XPD", "cloud.example.com:port"))
        assertEquals(JoinInput.InvalidServer, parseJoinInput("K7QM-3XPD", "user@cloud.example.com"))
    }

    @Test
    fun `the Server field is ignored when the input carries its own server`() {
        assertEquals(
            JoinInput.Code("https://cloud.example.com", "K7QM3XPD"),
            parseJoinInput("cloud.example.com/K7QM-3XPD", "other.example.org"),
        )
    }

    // Shares are matched on (server, token): joining the same list both ways must not make two.
    @Test
    fun `a code's server matches the same list's link`() {
        val link = parseShareLink("https://Cloud.Example.com:443/nextcloud/index.php/apps/shopping_list/s/$token")!!
        val code = parseJoinInput("cloud.example.com:443/nextcloud/K7QM-3XPD") as JoinInput.Code
        assertEquals(link.server, code.server)
    }

    @Test
    fun `finds an invite inside a pasted message`() {
        assertEquals(
            JoinInput.Code("https://cloud.example.com", "K7QM3XPD"),
            parseJoinInput("Join my list: cloud.example.com/K7QM-3XPD see you there"),
        )
        assertEquals(
            JoinInput.Link(ShareLink("https://cloud.example.com", token)),
            parseJoinInput("Here you go https://cloud.example.com/apps/shopping_list/s/$token"),
        )
    }

    @Test
    fun `junk is invalid`() {
        assertEquals(JoinInput.Invalid, parseJoinInput(""))
        assertEquals(JoinInput.Invalid, parseJoinInput("   "))
        assertEquals(JoinInput.Invalid, parseJoinInput("hello there"))
        assertEquals(JoinInput.Invalid, parseJoinInput("https://cloud.example.com/"))
    }
}
