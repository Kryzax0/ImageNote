package com.imagenote.app

import android.Manifest
import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.imagenote.app.speech.AudioPump
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPumpTest {
    @After fun tearDown() = AudioPump.stop()

    @Test
    fun audioRecordedBeforeTheRecogniserConnectsIsDelivered() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(AudioPump.start())
        AudioPump.claim()
        // The recogniser takes a while to start; audio keeps buffering meanwhile.
        Thread.sleep(400)
        val src = AudioPump.newSource()!!
        val input = ParcelFileDescriptor.AutoCloseInputStream(src)
        val buf = ByteArray(64_000)
        var total = 0
        val deadline = System.currentTimeMillis() + 3000
        while (total < 12_800 && System.currentTimeMillis() < deadline) {
            val n = input.read(buf)
            if (n > 0) total += n
        }
        // At least the 400 ms buffered before the pipe existed (12.8 kB at 16 kHz mono 16-bit).
        assertTrue("got $total bytes", total >= 12_800)
        input.close()
        // Next session gets a fresh pipe and the pump keeps going.
        val src2 = AudioPump.newSource()
        assertTrue(src2 != null)
        src2!!.close()
    }
}
