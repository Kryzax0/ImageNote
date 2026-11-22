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
        ShadowLooper.idleMainLooper()
        Thread.sleep(300)
        ShadowLooper.idleMainLooper()
        assertEquals(uri, vm.ui.value.current)
        val add = MainViewModel::class.java.getDeclaredMethod("addNote", String::class.java).apply { isAccessible = true }
        add.invoke(vm, "Buy this one")
        assertEquals(0.62f, vm.ui.value.notes.single().y, 0.001f)
        // Later notes stack normally below it.
        add.invoke(vm, "Second")
        assert(vm.ui.value.notes[1].y > 0.62f)
    }
}
