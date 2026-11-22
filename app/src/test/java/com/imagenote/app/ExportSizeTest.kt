package com.imagenote.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.Exporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExportSizeTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val limit = (10 * 1_000_000L * 0.95).toLong()
    private val notes = listOf(TextNote(text = "Size test note", y = 0.2f))

    @Test
    fun worstCaseNoiseFitsUnderTenMegabytes() {
        val w = 5600; val h = 4200 // ~23.5 MP
        val px = IntArray(w * h) { Random.nextInt() or (0xFF shl 24) }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val t = System.currentTimeMillis()
        val e = Exporter.encodeBest(ctx, bmp, notes, limit, preferPng = false)
        println("noise: ${e.width}x${e.height} ${e.bytes.size / 1e6} MB png=${e.png} in ${System.currentTimeMillis() - t} ms")
        assertTrue(e.bytes.size <= limit)
        assertTrue("should keep a large image", e.width >= 2000)
    }

    @Test
    fun screenshotStaysLosslessPng() {
        val bmp = Bitmap.createBitmap(2176, 1812, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF5F5F5.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; color = 0xFF202020.toInt() }
        for (i in 0 until 40) c.drawText("Line $i of some website text, prices and links", 40f, 60f + i * 44f, p)
        val e = Exporter.encodeBest(ctx, bmp, notes, limit, preferPng = true)
        println("screenshot: ${e.width}x${e.height} ${e.bytes.size / 1e6} MB png=${e.png}")
        assertTrue(e.png && e.width == 2176)
    }

    @Test
    fun ordinaryPhotoKeepsFullResolutionAtTopQuality() {
        val w = 4000; val h = 3000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Random(1)
        repeat(400) {
            p.color = r.nextInt() or (0xFF shl 24)
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 50f + r.nextFloat() * 400f, p)
        }
        val e = Exporter.encodeBest(ctx, bmp, notes, limit, preferPng = false)
        println("photo: ${e.width}x${e.height} ${e.bytes.size / 1e6} MB")
        assertTrue(e.width == w && e.bytes.size <= limit)
    }
}
