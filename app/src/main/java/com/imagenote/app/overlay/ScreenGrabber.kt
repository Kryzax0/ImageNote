package com.imagenote.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps one screen-capture session open for the whole of transparent mode and hands out a
 * single frame whenever the user taps the mic.
 *
 * Android 14+ allows only one VirtualDisplay per MediaProjection consent, so the display is
 * created once and resized (fold / unfold) instead of being recreated.
 */
class ScreenGrabber(
    private val context: Context,
    private val projection: MediaProjection,
    private val onStopped: () -> Unit,
) {
    private val thread = HandlerThread("ImageNoteCapture").also { it.start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var dpi = 0

    @Volatile private var wanted: CompletableDeferred<Bitmap?>? = null
    @Volatile private var notBeforeNanos = 0L

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            onStopped()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            handler.post { resize(width, height) }
        }
    }

    fun start() {
        val (w, h, d) = screenSize()
        width = w; height = h; dpi = d
        projection.registerCallback(callback, handler)
        val r = newReader(w, h)
        reader = r
        display = projection.createVirtualDisplay(
            "ImageNote", w, h, d,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler,
        )
    }

    /** Called on configuration changes (fold/unfold, rotation). */
    fun onScreenChanged() {
        // On Android 14+ onCapturedContentResize reports the real captured size instead.
        if (Build.VERSION.SDK_INT >= 34) return
        val (w, h, _) = screenSize()
        handler.post { resize(w, h) }
    }

    private fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || (w == width && h == height)) return
        val vd = display ?: return
        val old = reader
        val r = newReader(w, h)
        width = w; height = h
        vd.resize(w, h, dpi)
        vd.surface = r.surface
        reader = r
        old?.close()
    }

    private fun newReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3).apply {
            // Always drain frames so the virtual display never stalls; keep one only when asked.
            setOnImageAvailableListener({ rd ->
                val img = runCatching { rd.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    val d = wanted
                    if (d != null && img.timestamp >= notBeforeNanos) {
                        wanted = null
                        d.complete(runCatching { img.toBitmap() }.getOrNull())
                    }
                } finally {
                    img.close()
                }
            }, handler)
        }

    /**
     * Grabs the next frame drawn at least [settleMillis] after this call (the caller hides the
     * overlay first so it isn't in the picture).
     */
    suspend fun capture(settleMillis: Long = 120): Bitmap? {
        val d = CompletableDeferred<Bitmap?>()
        notBeforeNanos = System.nanoTime() + settleMillis * 1_000_000
        wanted = d
        val bmp = withTimeoutOrNull(1500) { d.await() }
        if (bmp != null) return bmp
        // The screen may be completely static: accept any frame.
        val d2 = CompletableDeferred<Bitmap?>()
        notBeforeNanos = 0
        wanted = d2
        return withTimeoutOrNull(800) { d2.await() }.also { wanted = null }
    }

    fun stop() {
        wanted?.complete(null)
        runCatching { projection.unregisterCallback(callback) }
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection.stop() }
        thread.quitSafely()
    }

    private fun screenSize(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val dpi = context.resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Triple(b.width(), b.height(), dpi)
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            Triple(m.widthPixels, m.heightPixels, dpi)
        }
    }
}

private fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowPadding = plane.rowStride - pixelStride * width
    val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)
    return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
}
