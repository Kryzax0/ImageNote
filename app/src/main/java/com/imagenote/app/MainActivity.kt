package com.imagenote.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.imagenote.app.overlay.OverlayService
import com.imagenote.app.ui.FoldPosture
import com.imagenote.app.ui.ImageNoteApp
import com.imagenote.app.ui.MainViewModel
import com.imagenote.app.ui.theme.ImageNoteTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_CAPTURE = "com.imagenote.app.OPEN_CAPTURE"
    }

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val layoutInfo = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
        setContent {
            val info by layoutInfo.collectAsStateWithLifecycle(WindowLayoutInfo(emptyList()))
            val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            // Only treat the hinge as a divider when the phone is actually bent (Flex mode / book).
            val posture = if (fold != null && fold.state == FoldingFeature.State.HALF_OPENED) {
                if (fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                    FoldPosture(horizontalHinge = fold.bounds)
                } else FoldPosture(verticalHinge = fold.bounds)
            } else FoldPosture()

            ImageNoteTheme {
                ImageNoteApp(vm, posture)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        OverlayService.appVisible.value = true
    }

    override fun onStop() {
        OverlayService.appVisible.value = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Share" or "Open with" from Samsung Gallery and other apps. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            ACTION_OPEN_CAPTURE -> { intent.data?.let(vm::openCapture); return }
            else -> null
        }
        uri?.let(vm::openExternal)
    }
}
