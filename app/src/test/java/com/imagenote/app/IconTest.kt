package com.imagenote.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the launcher icon with a circle and a squircle-ish mask for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconTest {
    @Test
    fun renderIcon() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val size = 432
        val out = Bitmap.createBitmap(size * 2 + 60, size + 40, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF202024.toInt())
        listOf(0, 1).forEach { i ->
            val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach {
                ContextCompat.getDrawable(ctx, it)!!.apply { setBounds(0, 0, size, size); draw(lc) }
            }
            // Adaptive icons show the centre 72/108 of the layers.
            val visible = Bitmap.createBitmap(layer, size / 6, size / 6, size * 2 / 3, size * 2 / 3)
            val scaled = Bitmap.createScaledBitmap(visible, size, size, true)
            val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val mc = Canvas(mask)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (i == 0) mc.drawCircle(size / 2f, size / 2f, size / 2f, p)
            else mc.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.3f, size * 0.3f, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            mc.drawBitmap(scaled, 0f, 0f, p)
            c.drawBitmap(mask, 20f + i * (size + 20f), 20f, null)
        }
        File("build/screenshots").mkdirs()
        File("build/screenshots/icon.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
