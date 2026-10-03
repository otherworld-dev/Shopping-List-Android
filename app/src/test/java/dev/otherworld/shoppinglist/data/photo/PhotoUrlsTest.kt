package dev.otherworld.shoppinglist.data.photo

import dev.otherworld.shoppinglist.data.remote.PLACEHOLDER_BASE_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The web app's imagePaths tests, below index.php so they work without pretty URLs. */
class PhotoUrlsTest {

    private val own = PLACEHOLDER_BASE_URL + "index.php/apps/shopping_list"

    @Test
    fun `nothing for an item without a photo`() {
        assertNull(PhotoUrls.own(5, 42, null, PhotoSize.THUMBNAIL))
        assertNull(PhotoUrls.own(5, 42, "", PhotoSize.FULL))
        assertNull(PhotoUrls.public("https://cloud.example.com", "tok", 42, null, PhotoSize.THUMBNAIL))
    }

    @Test
    fun `points at the thumbnail or the full photo under the list and item`() {
        assertEquals("$own/lists/5/items/42/thumbnail/abcdefabcdefabcd", PhotoUrls.own(5, 42, "abcdefabcdefabcd", PhotoSize.THUMBNAIL))
        assertEquals("$own/lists/5/items/42/image/abcdefabcdefabcd", PhotoUrls.own(5, 42, "abcdefabcdefabcd", PhotoSize.FULL))
    }

    @Test
    fun `encodes the key`() {
        assertEquals("$own/lists/5/items/42/image/a%20b", PhotoUrls.own(5, 42, "a b", PhotoSize.FULL))
    }

    @Test
    fun `goes through the share token on the link's own server`() {
        assertEquals(
            "https://cloud.example.com/nc/index.php/apps/shopping_list/s/tok/items/42/thumbnail/abcdefabcdefabcd",
            PhotoUrls.public("https://cloud.example.com/nc/", "tok", 42, "abcdefabcdefabcd", PhotoSize.THUMBNAIL),
        )
        assertEquals(
            "https://cloud.example.com/index.php/apps/shopping_list/s/tok/items/42/image/abcdefabcdefabcd",
            PhotoUrls.public("https://cloud.example.com", "tok", 42, "abcdefabcdefabcd", PhotoSize.FULL),
        )
    }

    @Test
    fun `encodes the token`() {
        assertEquals(
            "https://cloud.example.com/index.php/apps/shopping_list/s/a%2Fb/items/42/image/abcdefabcdefabcd",
            PhotoUrls.public("https://cloud.example.com", "a/b", 42, "abcdefabcdefabcd", PhotoSize.FULL),
        )
    }
}
