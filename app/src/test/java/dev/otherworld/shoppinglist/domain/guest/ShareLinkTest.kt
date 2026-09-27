package dev.otherworld.shoppinglist.domain.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {

    private val token = "3f9a0c1d2e"

    @Test
    fun `reads a plain link`() {
        assertEquals(
            ShareLink("https://cloud.example.com", token),
            parseShareLink("https://cloud.example.com/apps/shopping_list/s/$token"),
        )
    }

    @Test
    fun `drops index php and a trailing slash`() {
        assertEquals(
            ShareLink("https://cloud.example.com", token),
            parseShareLink("https://cloud.example.com/index.php/apps/shopping_list/s/$token/"),
        )
    }

    @Test
    fun `keeps a sub-path install and a port`() {
        assertEquals(
            ShareLink("https://example.com:8443/nextcloud", token),
            parseShareLink("https://example.com:8443/nextcloud/index.php/apps/shopping_list/s/$token"),
        )
    }

    @Test
    fun `drops the default port, query and fragment, and lowercases the host`() {
        assertEquals(
            ShareLink("https://cloud.example.com", token),
            parseShareLink("https://Cloud.Example.com:443/apps/shopping_list/s/$token?x=1#top"),
        )
    }

    @Test
    fun `decodes an encoded token`() {
        assertEquals("abCd", parseShareLink("https://cloud.example.com/apps/shopping_list/s/ab%43d")?.token)
    }

    @Test
    fun `refuses anything that is not a share link`() {
        assertNull(parseShareLink("http://cloud.example.com/apps/shopping_list/s/$token"))
        assertNull(parseShareLink("https://cloud.example.com/apps/shopping_list/lists/12"))
        assertNull(parseShareLink("https://cloud.example.com/apps/shopping_list/s/"))
        assertNull(parseShareLink("https://cloud.example.com/apps/shopping_list/s/$token/items/4"))
        assertNull(parseShareLink("not a link"))
        assertNull(parseShareLink(""))
    }

    @Test
    fun `builds the link back`() {
        assertEquals(
            "https://example.com/nextcloud/apps/shopping_list/s/$token",
            ShareLink("https://example.com/nextcloud", token).url,
        )
    }
}
