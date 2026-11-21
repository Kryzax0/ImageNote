package com.imagenote.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.NoteRenderer
import com.imagenote.app.speech.VoiceState
import kotlin.math.abs

/**
 * The selected image with its notes drawn on top. Tap a note to select it, double-tap to edit
 * its text, drag to move, pinch to resize and twist to rotate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Editor(
    state: UiState,
    voice: VoiceState,
    vm: MainViewModel,
    onEditNote: (TextNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Accept images dragged in from Samsung Gallery / My Files in split screen or pop-up view.
    val dropTarget = remember(vm) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                activity?.requestDragAndDropPermissions(dragEvent)
                val clip = dragEvent.clipData ?: return false
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { vm.openExternal(it); return true }
                }
                return false
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { e ->
                    e.mimeTypes().any { ClipDescription.compareMimeTypes(it, "image/*") }
                },
                target = dropTarget,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val uri = state.current
        if (uri == null) {
            EmptyEditor()
            return@BoxWithConstraints
        }
        val aspect = if (state.aspect > 0f) state.aspect else 1f
        val boxAspect = maxWidth / maxHeight
        val (w, h) = if (aspect > boxAspect) maxWidth to maxWidth / aspect else maxHeight * aspect to maxHeight

        Box(Modifier.size(w, h)) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
                onState = { s ->
                    if (s is AsyncImagePainter.State.Success) {
                        val img = s.result.image
                        vm.onImageSize(uri, img.width, img.height)
                    }
                },
            )
            if (state.aspect > 0f) {
                NotesOverlay(state, voice, vm, onEditNote)
            }
        }

        // Live transcript banner while listening
        AnimatedVisibility(
            visible = voice is VoiceState.Listening,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
        ) {
            val v = voice as? VoiceState.Listening
            val secs = state.settings.silenceMillis / 1000f
            Text(
                text = if (v?.heardSpeech == true) "Listening… pause ${"%.1f".format(secs)}s to finish"
                else "Listening… start speaking",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xAA000000), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun NotesOverlay(
    state: UiState,
    voice: VoiceState,
    vm: MainViewModel,
    onEditNote: (TextNote) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val slop = with(density) { 12.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val latest by rememberUpdatedState(state)

    // Ghost preview of the note being spoken right now.
    val listening = voice as? VoiceState.Listening
    val preview = listening?.text?.takeIf { it.isNotBlank() }?.let { text ->
        val (x, y) = vm.nextNotePosition(text)
        TextNote(id = "preview", text = text, x = x, y = y, style = state.defaultStyle)
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { p ->
                        val hit = NoteRenderer.hitTest(
                            context, latest.notes, size.width.toFloat(), size.height.toFloat(), p.x, p.y, slop
                        )
                        vm.selectNote(hit?.id)
                    },
                    onDoubleTap = { p ->
                        val hit = NoteRenderer.hitTest(
                            context, latest.notes, size.width.toFloat(), size.height.toFloat(), p.x, p.y, slop
                        )
                        if (hit != null) { vm.selectNote(hit.id); onEditNote(hit) }
                    },
                )
            }
            .pointerInput(Unit) {
                // Drag / pinch / rotate the note under the finger (or the selected note when pinching).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val hit = NoteRenderer.hitTest(context, latest.notes, w, h, down.position.x, down.position.y, slop)
                    var targetId = hit?.id
                    var started = false
                    var totalPan = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (targetId == null && pressed.size >= 2) targetId = latest.selectedNoteId
                        val id = targetId ?: continue
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        val pan = event.calculatePan()
                        if (!started) {
                            totalPan += pan
                            if (totalPan.getDistance() > touchSlop || abs(zoom - 1f) > 0.02f || abs(rotation) > 2f) {
                                started = true
                                vm.selectNote(id)
                                vm.beginGesture()
                            }
                        }
                        if (started) {
                            vm.transformNote(id, pan.x / w, pan.y / h, zoom, rotation)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    if (started) vm.endGesture()
                }
            }
    ) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val w = size.width
            val h = size.height
            state.notes.forEach { note ->
                NoteRenderer.draw(nc, context, note, w, h, selected = note.id == state.selectedNoteId)
            }
            preview?.let { NoteRenderer.draw(nc, context, it, w, h, alpha = 0.75f) }
        }
    }
}

@Composable
private fun EmptyEditor() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(
            Icons.Outlined.Image, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Text(
            "Pick an image from the reel below, then tap the mic and speak.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        )
    }
}

/** Starts a system drag of a saved image so it can be dropped into another app in split screen. */
fun startImageDrag(view: View, uri: Uri) {
    val clip = ClipData.newUri(view.context.contentResolver, "ImageNote", uri)
    view.startDragAndDrop(
        clip,
        View.DragShadowBuilder(view),
        null,
        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
    )
}
