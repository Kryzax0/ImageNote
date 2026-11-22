package com.imagenote.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.imagenote.app.overlay.OverlayService
import com.imagenote.app.ui.theme.Graphite
import com.imagenote.app.ui.theme.NeonOrange

/**
 * Walks through what transparent mode needs, in order:
 * microphone → "display over other apps" → notifications (optional) → screen-capture consent,
 * then starts the floating bar and sends ImageNote to the background.
 */
class TransparentModeController internal constructor() {
    var showIntro by mutableStateOf(false)
    internal var proceed: () -> Unit = {}
    lateinit var toggle: () -> Unit
        internal set
}

@Composable
fun rememberTransparentMode(vm: MainViewModel): TransparentModeController {
    val context = LocalContext.current
    val controller = remember { TransparentModeController() }
    val prefs = remember { context.getSharedPreferences("overlay", Context.MODE_PRIVATE) }

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            OverlayService.start(context, r.resultCode, data)
            (context as? Activity)?.moveTaskToBack(true)
        } else vm.toast("Transparent mode needs permission to see the screen")
    }
    val askProjection = {
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        projection.launch(mpm.createScreenCaptureIntent())
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askProjection() }
    val askNotificationsThenProjection = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("askedNotif", false)
        ) {
            prefs.edit().putBoolean("askedNotif", true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else askProjection()
    }
    val overlaySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) askNotificationsThenProjection()
        else vm.toast("Allow \"Display over other apps\" to use transparent mode")
    }
    val askOverlay = {
        if (Settings.canDrawOverlays(context)) askNotificationsThenProjection()
        else {
            vm.toast("Turn on \"Allow display over other apps\" for ImageNote")
            overlaySettings.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askOverlay() else vm.toast("Microphone permission is needed for voice notes")
    }
    val begin = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) askOverlay()
        else mic.launch(Manifest.permission.RECORD_AUDIO)
    }

    controller.proceed = {
        prefs.edit().putBoolean("introShown", true).apply()
        controller.showIntro = false
        begin()
    }
    controller.toggle = {
        when {
            OverlayService.running.value -> { OverlayService.stop(context); vm.toast("Transparent mode off") }
            !prefs.getBoolean("introShown", false) -> controller.showIntro = true
            else -> begin()
        }
    }
    return controller
}

@Composable
fun TransparentIntroDialog(c: TransparentModeController) {
    AlertDialog(
        onDismissRequest = { c.showIntro = false },
        containerColor = Graphite,
        title = { Text("Transparent mode") },
        text = {
            Column {
                Text("ImageNote shrinks to a small floating bar so you can use your phone normally: open apps, browse websites, anything.")
                Text(
                    "Tap the orange mic on any screen. ImageNote captures that screen, you speak, and your words are written onto it as a note.",
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "• Drag the bar up or down to choose where the note goes.\n" +
                        "• Captures are saved to Pictures/ImageNote/Screens, and you can edit them in the app.\n" +
                        "• Android will ask to share your screen. Choose \"Share entire screen\".\n" +
                        "• Tap ✕ on the bar to exit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = c.proceed) { Text("Start", color = NeonOrange) } },
        dismissButton = { TextButton(onClick = { c.showIntro = false }) { Text("Not now") } },
    )
}
