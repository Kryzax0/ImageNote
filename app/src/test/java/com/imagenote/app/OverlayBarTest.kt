package com.imagenote.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
    fun drawsFloatingBar() {
        val svc = Robolectric.buildService(OverlayService::class.java).create().get()
        OverlayService::class.java.getDeclaredMethod("showOverlay").apply { isAccessible = true }.invoke(svc)
        OverlayService::class.java.getDeclaredMethod("showMessage", String::class.java)
            .apply { isAccessible = true }.invoke(svc, "Opening ImageNote…")
        val root = OverlayService::class.java.getDeclaredField("root").apply { isAccessible = true }.get(svc) as View
        root.measure(View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1708, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bmp = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF7FA7C9.toInt()); root.draw(c)
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "overlay_bar.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
