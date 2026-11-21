package com.imagenote.app.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.imagenote.app.data.TextNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

object Exporter {

    /** Fold 7 photos can be 200 MP; cap the export so it fits comfortably in memory. */
    private const val MAX_PIXELS = 24_000_000

    /**
     * Renders [notes] onto a full-resolution copy of [source] and saves it to Pictures/ImageNote.
     * The original image is never modified. Returns the new MediaStore uri.
     */
    suspend fun export(
        context: Context,
        source: Uri,
        notes: List<TextNote>,
        jpegQuality: Int,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source).orEmpty()
        val asPng = mime.contains("png")

        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val px = info.size.width.toLong() * info.size.height
            if (px > MAX_PIXELS) {
                val f = sqrt(MAX_PIXELS.toDouble() / px)
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, true) else it }

        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        notes.forEach { NoteRenderer.draw(canvas, context, it, w, h) }

        val transcript = notes.joinToString("\n") { it.text }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (asPng) "png" else "jpg"
        val tmp = File(context.cacheDir, "export_$stamp.$ext")
        tmp.outputStream().use { out ->
            bitmap.compress(
                if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                jpegQuality.coerceIn(50, 100), out
            )
        }
        bitmap.recycle()

        if (!asPng) runCatching { writeExif(context, source, tmp, transcript) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ImageNote_$stamp.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, if (asPng) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageNote")
            put(MediaStore.Images.Media.DESCRIPTION, transcript.take(1000))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Couldn't create the image file")
        try {
            resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write the image file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        } finally {
            tmp.delete()
        }
        uri
    }

    /** Keep useful metadata from the original and embed the spoken notes as text. */
    private fun writeExif(context: Context, source: Uri, target: File, transcript: String) {
        val out = ExifInterface(target.absolutePath)
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                val src = ExifInterface(input)
                listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                ).forEach { tag -> src.getAttribute(tag)?.let { out.setAttribute(tag, it) } }
            }
        }
        // The decoder already applied rotation, so the saved pixels are upright.
        out.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        out.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, transcript)
        out.setAttribute(ExifInterface.TAG_USER_COMMENT, transcript)
        out.setAttribute(ExifInterface.TAG_SOFTWARE, "ImageNote")
        out.saveAttributes()
    }
}
