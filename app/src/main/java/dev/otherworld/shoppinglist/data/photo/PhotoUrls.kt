package dev.otherworld.shoppinglist.data.photo

import dev.otherworld.shoppinglist.data.remote.PLACEHOLDER_BASE_URL
import java.net.URLEncoder

enum class PhotoSize(val segment: String) { THUMBNAIL("thumbnail"), FULL("image") }

/**
 * Where an item's photo is served from: plain routes outside OCS, since they answer with the
 * JPEG itself. The key is part of the path and changes whenever the photo does, so a URL can
 * be cached for good. Mirrors the web app's imagePaths.ts.
 */
object PhotoUrls {
    private const val APP = "index.php/apps/shopping_list"

    /**
     * A photo on the user's own server, on the placeholder host so the API client's auth
     * interceptor fills in the server, its subpath and the login. Null without a photo.
     */
    fun own(listId: Long, itemId: Long, imageKey: String?, size: PhotoSize): String? {
        if (imageKey.isNullOrEmpty()) return null
        return "$PLACEHOLDER_BASE_URL$APP/lists/$listId/items/$itemId/${size.segment}/${encode(imageKey)}"
    }

    /** The same photo reached through a share link's token, by the item's id on that server. */
    fun public(server: String, token: String, remoteItemId: Long, imageKey: String?, size: PhotoSize): String? {
        if (imageKey.isNullOrEmpty()) return null
        return "${server.trimEnd('/')}/$APP/s/${encode(token)}/items/$remoteItemId/${size.segment}/${encode(imageKey)}"
    }

    /** encodeURIComponent, as the web app uses: a space is %20, not +. */
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Both sizes of one item's photo. */
data class ItemPhotoUrls(val thumbnail: String, val full: String)
