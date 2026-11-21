package com.imagenote.app

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.imagenote.app.data.BackgroundStyle
import com.imagenote.app.data.NoteStyle
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.NoteRenderer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private val OUT = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

private fun save(bmp: Bitmap, name: String) =
    File(OUT, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

private fun samplePhoto(w: Int, h: Int): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    c.drawPaint(Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF2E6FAE.toInt(), 0xFFE9C46A.toInt(), 0xFF6B8F71.toInt()), null, Shader.TileMode.CLAMP)
    })
    // a bright area to prove the outline keeps white text readable
    c.drawCircle(w * 0.7f, h * 0.3f, w * 0.18f, Paint().apply { color = 0xFFFFF8E1.toInt() })
    return b
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RendererTest {
    @Test
    fun rendersOutlinedItalicNotes() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val bmp = samplePhoto(1200, 900)
        val canvas = Canvas(bmp)
        val notes = listOf(
            TextNote(text = "Fix the railing before Friday", x = 0.5f, y = 0.15f),
            TextNote(text = "Paint: warm white", x = 0.5f, y = 0.32f, style = NoteStyle(fontId = "dancing_script")),
            TextNote(text = "Ask Sam about the lights", x = 0.35f, y = 0.55f, rotation = -8f,
                style = NoteStyle(fontId = "permanent_marker", size = 45f, slant = 0f)),
            TextNote(text = "Measure window 2", x = 0.3f, y = 0.8f,
                style = NoteStyle(fontId = "pacifico", size = 45f, textColor = 0xFFFFE14D.toInt(), background = BackgroundStyle.TRANSLUCENT)),
            TextNote(text = "Neon glow", x = 0.78f, y = 0.72f,
                style = NoteStyle(fontId = "satisfy", background = BackgroundStyle.GLOW, backgroundColor = 0xFFFF7A1A.toInt(), outlineWidth = 0.04f)),
            TextNote(text = "Highlighter", x = 0.78f, y = 0.9f,
                style = NoteStyle(fontId = "kalam", size = 45f, textColor = 0xFF000000.toInt(), outlineWidth = 0f, background = BackgroundStyle.HIGHLIGHT, backgroundColor = 0xFFFF7A1A.toInt())),
        )
        notes.forEachIndexed { i, n -> NoteRenderer.draw(canvas, ctx, n, 1200f, 900f, selected = i == 2) }
        save(bmp, "renderer")
        // White fill + black outline must both be present.
        var white = 0; var black = 0
        for (y in 0 until 300 step 2) for (x in 0 until 1200 step 2) {
            val p = bmp.getPixel(x, y)
            if (p == 0xFFFFFFFF.toInt()) white++
            if (p == 0xFF000000.toInt()) black++
        }
        assertTrue("white=$white black=$black", white > 200 && black > 200)
    }
}

abstract class AppScreenshot {
    private fun settle() {
        repeat(20) {
            rule.mainClock.advanceTimeBy(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    fun shoot(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.RECORD_AUDIO)
        val f = File(app.cacheDir, "photo.png")
        f.outputStream().use { samplePhoto(1600, 1200).compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[com.imagenote.app.ui.MainViewModel::class.java]
            vm.openExternal(Uri.fromFile(f))
            vm.onImageSize(Uri.fromFile(f), 1600, 1200)
        }
        settle()
        rule.runOnUiThread {
            val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[com.imagenote.app.ui.MainViewModel::class.java]
            // Simulate two spoken notes
            val m = vm.javaClass.getDeclaredMethod("addNote", String::class.java).apply { isAccessible = true }
            m.invoke(vm, "Replace this lamp")
            m.invoke(vm, "Check the price online later")
        }
        settle()
        var bmp: Bitmap? = null
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        save(bmp!!, name)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w904dp-h1013dp-xhdpi")
class InnerScreenTest : AppScreenshot() {
    @Test fun innerScreen() = shoot("inner_screen")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w384dp-h854dp-xhdpi")
class CoverScreenTest : AppScreenshot() {
    @Test fun coverScreen() = shoot("cover_screen")
}
