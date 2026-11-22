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

    /** Fold 7 photos can be 200 MP; work at up to 24 MP (≈5650×4240), plenty for any screen or print. */
    private const val MAX_PIXELS = 24_000_000

    class Saved(val uri: Uri, val bytes: Int, val width: Int, val height: Int, val png: Boolean)

    class Encoded(val bytes: ByteArray, val png: Boolean, val width: Int, val height: Int)

    /**
     * Renders [notes] onto a full-resolution copy of [source] and saves it to Pictures/ImageNote,
     * in the best quality that fits under [maxMegabytes] (0 = no limit). The original is untouched.
     */
    suspend fun export(
        context: Context,
        source: Uri,
        notes: List<TextNote>,
        maxMegabytes: Int,
    ): Saved = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source).orEmpty()
        // Screenshots and PNGs keep lossless quality when they fit; text stays perfectly crisp.
        val preferPng = mime.contains("png") || source.path.orEmpty().endsWith(".png", ignoreCase = true)

        val base = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val px = info.size.width.toLong() * info.size.height
            if (px > MAX_PIXELS) {
                val f = sqrt(MAX_PIXELS.toDouble() / px)
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
        val limit = if (maxMegabytes <= 0) Long.MAX_VALUE else (maxMegabytes * 1_000_000L * 0.95).toLong()
        val enc = encodeBest(context, base, notes, limit, preferPng)
        base.recycle()

        val transcript = notes.joinToString("\n") { it.text }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (enc.png) "png" else "jpg"
        val tmp = File(context.cacheDir, "export_$stamp.$ext")
        tmp.writeBytes(enc.bytes)
        if (!enc.png) runCatching { writeExif(context, source, tmp, transcript) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ImageNote_$stamp.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, if (enc.png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageNote")
            put(MediaStore.Images.Media.DESCRIPTION, transcript.take(1000))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Couldn't create the image file")
        val size = tmp.length().toInt()
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
        Saved(normalize(uri), size, enc.width, enc.height, enc.png)
    }

    /**
     * Highest quality that fits in [limitBytes]:
     * 1. lossless PNG if preferred and small enough;
     * 2. JPEG at quality 97, else the highest quality from 96 down to 82 that fits;
     * 3. only if even quality 82 is too big, shrink the image a little and try again.
     * Notes are drawn after any resizing so the handwriting stays sharp.
     */
    fun encodeBest(context: Context, base: Bitmap, notes: List<TextNote>, limitBytes: Long, preferPng: Boolean): Encoded {
        var scale = 1.0
        repeat(8) {
            val img = render(context, base, notes, scale)
            try {
                if (preferPng) {
                    val png = compress(img, Bitmap.CompressFormat.PNG, 100)
                    if (png.size <= limitBytes) return Encoded(png, true, img.width, img.height)
                }
                val top = compress(img, Bitmap.CompressFormat.JPEG, if (limitBytes == Long.MAX_VALUE) 98 else 97)
                if (top.size <= limitBytes) return Encoded(top, false, img.width, img.height)
                val floor = compress(img, Bitmap.CompressFormat.JPEG, 82)
                if (floor.size > limitBytes) {
                    // Too detailed even at q82: shrink so q~90 should fit, then search again.
                    scale *= sqrt(limitBytes.toDouble() / floor.size) * 0.9
                    return@repeat
                }
                var best = floor
                var lo = 83
                var hi = 96
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val b = compress(img, Bitmap.CompressFormat.JPEG, mid)
                    if (b.size <= limitBytes) { best = b; lo = mid + 1 } else hi = mid - 1
                }
                return Encoded(best, false, img.width, img.height)
            } finally {
                img.recycle()
            }
        }
        // Practically unreachable; fall back to a small, safe JPEG.
        val img = render(context, base, notes, scale)
        return Encoded(compress(img, Bitmap.CompressFormat.JPEG, 80), false, img.width, img.height).also { img.recycle() }
    }

    private fun render(context: Context, base: Bitmap, notes: List<TextNote>, scale: Double): Bitmap {
        val w = (base.width * scale).toInt().coerceAtLeast(1)
        val h = (base.height * scale).toInt().coerceAtLeast(1)
        val img = if (scale >= 0.999) base.copy(Bitmap.Config.ARGB_8888, true)
        else Bitmap.createScaledBitmap(base, w, h, true).let {
            if (it.isMutable && it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true)
        }
        val canvas = Canvas(img)
        notes.forEach { NoteRenderer.draw(canvas, context, it, img.width.toFloat(), img.height.toFloat()) }
        return img
    }

    private fun compress(img: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(4 * 1024 * 1024)
        img.compress(format, quality, out)
        return out.toByteArray()
    }

    /** Folder for transparent-mode captures. They stay private to the app until you Save. */
    fun capturesDir(context: Context) = File(context.filesDir, "captures").apply { mkdirs() }

    fun isCapture(context: Context, uri: Uri): Boolean =
        uri.scheme == "file" && uri.path?.startsWith(capturesDir(context).absolutePath) == true

    /**
     * Stores a raw screen capture privately (not in the gallery). Saving it later produces the only
     * gallery image, with the notes on it, and the private capture is removed.
     */
    suspend fun saveScreenshot(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(capturesDir(context), "Screen_$stamp.png")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Uri.fromFile(f)
    }

    /** MediaStore returns "external_primary" uris on insert; the gallery list uses "external". */
    fun normalize(uri: Uri): Uri =
        if (uri.authority == MediaStore.AUTHORITY && uri.pathSegments.size >= 4 && uri.pathSegments[1] == "images") {
            runCatching {
                android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    android.content.ContentUris.parseId(uri),
                )
            }.getOrDefault(uri)
        } else uri

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
