package dev.otherworld.shoppinglist.data.repo

import android.net.Uri
import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.photo.PhotoProcessor
import dev.otherworld.shoppinglist.data.photo.PhotoTooLargeException
import dev.otherworld.shoppinglist.data.photo.clearImageKeys
import dev.otherworld.shoppinglist.data.photo.spreadImageKey
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.domain.model.ItemModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photos on items. Online-direct, like the web app: the offline queue carries JSON, not
 * pictures, so callers check the connection first. A photo belongs to the item's name, so the
 * server puts it on every same-named item the user can change; the phone does the same at once.
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val service: OcsService,
    private val db: AppDatabase,
    private val processor: PhotoProcessor,
    private val listSettings: ListSettingsRepository,
) {
    private val itemDao = db.itemDao()

    /** Shrinks the picture at [source] and attaches it to [item], replacing any earlier one. */
    suspend fun attach(item: ItemModel, source: Uri) {
        val jpeg = processor.prepare(source)
        if (jpeg.size > listSettings.maxUploadBytes) throw PhotoTooLargeException()
        val part = MultipartBody.Part.createFormData("image", "image.jpg", jpeg.toRequestBody(JPEG))
        val updated = service.uploadItemImage(item.listId, item.id, part).ocs.data
        val key = updated.imageKey ?: return
        db.withTransaction {
            itemDao.getById(item.id)?.let { itemDao.update(it.copy(imageKey = key)) }
            spreadImageKey(itemDao.inEditableOwnLists(), updated.name, key).forEach { itemDao.update(it) }
        }
    }

    /** Takes the photo off [item]; the server forgets it for that name, so it goes from them all. */
    suspend fun remove(item: ItemModel) {
        service.removeItemImage(item.listId, item.id)
        db.withTransaction {
            itemDao.getById(item.id)?.let { itemDao.update(it.copy(imageKey = null)) }
            clearImageKeys(itemDao.inEditableOwnLists(), item.imageKey, item.name).forEach { itemDao.update(it) }
        }
    }

    fun newCameraTarget(): Uri = processor.newCameraTarget()

    fun discard(source: Uri) = processor.discard(source)

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
    }
}
