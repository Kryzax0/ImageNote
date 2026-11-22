package com.imagenote.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.imagenote.app.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureHandoffTest {
    @Test
    fun firstNoteOfCaptureGoesWhereTheBarWas() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val f = File(app.cacheDir, "shot.png")
        f.outputStream().use { Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = Uri.fromFile(f)
        val vm = MainViewModel(app)
        vm.openCapture(uri, noteY = 0.62f, listen = false)
        waitFor { vm.ui.value.current == uri }
        assertEquals(uri, vm.ui.value.current)
        val add = MainViewModel::class.java.getDeclaredMethod("addNote", String::class.java).apply { isAccessible = true }
        add.invoke(vm, "Buy this one")
        assertEquals(0.62f, vm.ui.value.notes.single().y, 0.001f)
        // Later notes stack normally below it.
        add.invoke(vm, "Second")
        assert(vm.ui.value.notes[1].y > 0.62f)
    }

    private fun waitFor(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!cond() && System.currentTimeMillis() < end) { ShadowLooper.idleMainLooper(); Thread.sleep(20) }
    }

    @Test
    fun captureIsPrivateListedOnceAndDiscardable() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bmp = Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888)
        val uri = kotlinx.coroutines.runBlocking { com.imagenote.app.render.Exporter.saveScreenshot(app, bmp) }
        assertEquals("file", uri.scheme)
        val vm = MainViewModel(app)
        vm.openCapture(uri, noteY = 0.5f)
        waitFor { vm.ui.value.current == uri }
        assertEquals(uri, vm.ui.value.current)
        assertEquals(1, vm.ui.value.images.count { it.uri == uri })
        assert(vm.isCapture(uri))
        vm.discardCapture(uri)
        waitFor { vm.ui.value.images.none { it.uri == uri } }
        assertEquals(0, vm.ui.value.images.count { it.uri == uri })
        assert(!File(uri.path!!).exists())
    }

    @Test
    fun mediaStoreUrisAreNormalised() {
        val a = Uri.parse("content://media/external_primary/images/media/42")
        assertEquals("content://media/external/images/media/42", com.imagenote.app.render.Exporter.normalize(a).toString())
    }
}
