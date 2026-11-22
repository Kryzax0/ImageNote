package com.imagenote.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.imagenote.app.data.TextNote
import com.imagenote.app.overlay.OverlayService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w384dp-h854dp-xhdpi")
class OverlayBarTest {
    @Test
    fun drawsFloatingBarWithCard() {
        val svc = Robolectric.buildService(OverlayService::class.java).create().get()
        OverlayService::class.java.getDeclaredMethod("showOverlay").apply { isAccessible = true }.invoke(svc)
        // Show the "note added" card with a fake screenshot.
        val shot = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3A6EA5.toInt()) }
        val preview = OverlayService::class.java.getDeclaredMethod("previewOf", Bitmap::class.java, TextNote::class.java)
            .apply { isAccessible = true }.invoke(svc, shot, TextNote(text = "Compare prices here", y = 0.4f)) as Bitmap
        OverlayService::class.java.getDeclaredMethod("showCard", Bitmap::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(svc, preview, "Compare prices here")
        val root = OverlayService::class.java.getDeclaredField("root").apply { isAccessible = true }.get(svc) as View
        root.measure(View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1708, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bmp = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF7FA7C9.toInt()); root.draw(c)
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "overlay_bar.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
