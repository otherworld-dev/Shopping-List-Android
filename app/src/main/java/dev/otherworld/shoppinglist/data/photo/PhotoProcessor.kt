package dev.otherworld.shoppinglist.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The picked file could not be read as a picture. */
class PhotoDecodeException(cause: Throwable? = null) : IOException("Could not decode image", cause)

/** Even shrunk, the photo is more than the server takes. */
class PhotoTooLargeException : IOException("Image is too large")

/**
 * Turns a picked or taken picture into the JPEG that's uploaded: upright, shrunk to
 * [PHOTO_MAX_EDGE] and re-encoded. Always re-encoded, unlike the web app (which sends the
 * original when that's smaller), so a HEIC photo, which the server refuses, goes up as JPEG
 * and its location never leaves the phone.
 */
@Singleton
class PhotoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cameraDir get() = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }

    suspend fun prepare(source: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(source) else decodeLegacy(source)
        } catch (e: OutOfMemoryError) {
            throw PhotoDecodeException(e)
        } catch (e: Exception) {
            throw PhotoDecodeException(e)
        }
        try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)) throw PhotoDecodeException()
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private val authority get() = "${context.packageName}.photos"

    /** A fresh file for the camera app to write into, shared through the FileProvider. */
    fun newCameraTarget(): Uri {
        // Any left behind by a process that died mid-upload.
        val stale = System.currentTimeMillis() - STALE_MS
        cameraDir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
        val file = File(cameraDir, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** Throws away a camera picture once uploaded or abandoned; a picked one isn't ours to touch. */
    fun discard(source: Uri) {
        if (source.authority != authority) return
        source.lastPathSegment?.let { File(cameraDir, it).delete() }
    }

    // ImageDecoder reads HEIC too, and applies the EXIF rotation itself.
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(source: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            val (w, h) = shrunkSize(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            // A software bitmap, so it can be compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeLegacy(source: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw PhotoDecodeException()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoDecodeException()
        val options = BitmapFactory.Options().apply { inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight) }
        val sampled = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw PhotoDecodeException()
        val rotation = resolver.openInputStream(source)?.use { exifRotation(ExifInterface(it)) } ?: 0
        val (w, h) = shrunkSize(sampled.width, sampled.height)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / sampled.width, h.toFloat() / sampled.height)
            postRotate(rotation.toFloat())
        }
        val result = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        if (result !== sampled) sampled.recycle()
        return result
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private companion object {
        const val CAMERA_DIR = "photos"
        const val STALE_MS = 60L * 60 * 1000
    }
}
