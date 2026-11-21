package com.imagenote.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.NoteRenderer
import com.imagenote.app.speech.VoiceState
import com.imagenote.app.ui.theme.Graphite
import com.imagenote.app.ui.theme.Ink
import com.imagenote.app.ui.theme.NeonOrange
import com.imagenote.app.ui.theme.OrangeGradient
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private val Glass = Color(0xE6141418)

/**
 * The selected image with its notes drawn on top.
 *
 * - Tap a note to select it; tap empty space to deselect.
 * - Drag to move (snaps to the centre lines), pinch to resize, twist to rotate (snaps to 0/45/90°).
 * - Corner handles: ✕ deletes, the bottom-right handle resizes and rotates with one finger.
 * - Double-tap or long-press a note to edit its text; long-press empty space to type a new note there.
 * - The floating toolbar has more actions (dictate more, size, duplicate, layer, lock, delete…).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Editor(
    state: UiState,
    voice: VoiceState,
    vm: MainViewModel,
    onEditNote: (TextNote) -> Unit,
    onTypeNote: (x: Float?, y: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val density = LocalDensity.current

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

    var hideNotes by remember { mutableStateOf(false) }
    var guideV by remember { mutableStateOf(false) }
    var guideH by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1A120C), Ink), radius = 1400f))
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
        val pad = 10.dp
        val areaW = maxWidth - pad * 2
        val areaH = maxHeight - pad * 2
        val aspect = if (state.aspect > 0f) state.aspect else 1f
        val (w, h) = if (aspect > areaW / areaH) areaW to areaW / aspect else areaH * aspect to areaH
        val originX = (maxWidth - w) / 2
        val originY = (maxHeight - h) / 2
        val wPx = with(density) { w.toPx() }
        val editorWPx = constraints.maxWidth.toFloat()
        val editorHPx = constraints.maxHeight.toFloat()
        val hPx = with(density) { h.toPx() }

        Box(
            Modifier
                .size(w, h)
                .shadow(18.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(6.dp))
        ) {
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
            if (state.aspect > 0f && !hideNotes) {
                NotesOverlay(
                    state, voice, vm, onEditNote, onTypeNote,
                    onGuides = { v, hz -> guideV = v; guideH = hz },
                )
            }
            // Snap guides
            if (guideV || guideH) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    if (guideV) drawLine(NeonOrange, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3f, pathEffect = dash)
                    if (guideH) drawLine(NeonOrange, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f, pathEffect = dash)
                }
            }
        }

        // Handles + floating toolbar for the selected note. Placed at editor level so they can
        // extend past the image edges and still receive touches.
        Box(Modifier.fillMaxSize()) {
            val sel = state.selectedNote
            if (sel != null && state.aspect > 0f && !hideNotes && voice !is VoiceState.Listening) {
                val frame = NoteRenderer.frame(context, sel, wPx, hPx)
                val ox = with(density) { originX.toPx() }
                val oy = with(density) { originY.toPx() }
                if (!sel.locked) {
                    val (dx, dy) = frame.corner(-1, -1)
                    Handle(Icons.Filled.Close, "Delete note", ox + dx, oy + dy, Color(0xFFFF4D4D)) {
                        Modifier.clickable { vm.deleteNote(sel.id) }
                    }
                    val (rx, ry) = frame.corner(1, 1)
                    ResizeRotateHandle(sel, ox + rx, oy + ry, ox, oy, wPx, hPx, vm)
                }
                FloatingToolbar(
                    note = sel,
                    anchorTop = oy + frame.bounds().top,
                    anchorBottom = oy + frame.bounds().bottom,
                    anchorCenterX = ox + frame.bounds().centerX(),
                    maxWidthPx = editorWPx,
                    maxHeightPx = editorHPx,
                    vm = vm,
                    onEdit = { onEditNote(sel) },
                )
            }
        }

        // Top-left quick actions
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickChip(Icons.Filled.TextFields, "Type") { onTypeNote(null, null) }
            if (state.notes.isNotEmpty()) {
                // Press and hold to see the original photo without notes.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Glass)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                hideNotes = true
                                tryAwaitRelease()
                                hideNotes = false
                            })
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(" Hold: original", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Live transcript banner while listening
        AnimatedVisibility(
            visible = voice is VoiceState.Listening,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
        ) {
            val v = voice as? VoiceState.Listening
            val secs = state.settings.silenceMillis / 1000f
            val what = if (state.appendTargetId != null) "Adding to note" else "Listening"
            Text(
                text = if (v?.heardSpeech == true) "$what… pause ${"%.1f".format(secs)}s to finish"
                else "$what… start speaking",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Black,
                modifier = Modifier
                    .background(OrangeGradient, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
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
    onTypeNote: (Float?, Float?) -> Unit,
    onGuides: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val slop = with(density) { 12.dp.toPx() }
    val snapPx = with(density) { 10.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val latest by rememberUpdatedState(state)

    // Live preview of what is being spoken right now.
    val listening = voice as? VoiceState.Listening
    val spoken = listening?.text?.takeIf { it.isNotBlank() }
    val appendTarget = state.appendTargetId
    val preview = spoken?.takeIf { appendTarget == null }?.let { text ->
        val (x, y) = vm.nextNotePosition(text)
        TextNote(id = "preview", text = text, x = x, y = y, style = state.defaultStyle)
    }

    fun hit(p: Offset, w: Float, h: Float) =
        NoteRenderer.hitTest(context, latest.notes, w, h, p.x, p.y, slop)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { p -> vm.selectNote(hit(p, size.width.toFloat(), size.height.toFloat())?.id) },
                    onDoubleTap = { p ->
                        hit(p, size.width.toFloat(), size.height.toFloat())?.let { vm.selectNote(it.id); onEditNote(it) }
                    },
                    onLongPress = { p ->
                        val n = hit(p, size.width.toFloat(), size.height.toFloat())
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (n != null) { vm.selectNote(n.id); onEditNote(n) }
                        else onTypeNote(p.x / size.width, p.y / size.height)
                    },
                )
            }
            .pointerInput(Unit) {
                // Drag / pinch / rotate the note under the finger (or the selected note when pinching).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var target = hit(down.position, w, h)
                    var started = false
                    var totalPan = Offset.Zero
                    var rawX = 0f; var rawY = 0f; var rawS = 1f; var rawR = 0f
                    var wasV = false; var wasH = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (target == null && pressed.size >= 2) target = latest.selectedNote
                        val note = target ?: continue
                        if (note.locked) continue
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        val pan = event.calculatePan()
                        if (!started) {
                            totalPan += pan
                            if (totalPan.getDistance() > touchSlop || abs(zoom - 1f) > 0.03f || abs(rotation) > 3f) {
                                started = true
                                val cur = latest.notes.firstOrNull { it.id == note.id } ?: note
                                rawX = cur.x; rawY = cur.y; rawS = cur.scale; rawR = cur.rotation
                                vm.selectNote(note.id)
                                vm.beginGesture()
                            }
                        }
                        if (started) {
                            rawX += pan.x / w; rawY += pan.y / h
                            rawS *= zoom; rawR += rotation
                            val snapV = abs(rawX - 0.5f) * w < snapPx
                            val snapH = abs(rawY - 0.5f) * h < snapPx
                            if ((snapV && !wasV) || (snapH && !wasH)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            wasV = snapV; wasH = snapH
                            onGuides(snapV, snapH)
                            vm.setTransform(
                                note.id,
                                if (snapV) 0.5f else rawX,
                                if (snapH) 0.5f else rawY,
                                rawS,
                                snapAngle(rawR),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    onGuides(false, false)
                    if (started) vm.endGesture()
                }
            }
    ) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val w = size.width
            val h = size.height
            state.notes.forEach { note ->
                val shown = if (note.id == appendTarget && spoken != null) note.copy(text = note.text + " " + spoken) else note
                NoteRenderer.draw(nc, context, shown, w, h, selected = note.id == state.selectedNoteId && listening == null)
            }
            preview?.let { NoteRenderer.draw(nc, context, it, w, h, alpha = 0.75f) }
        }
    }
}

/** Snap rotation to multiples of 45° when within 4°. */
private fun snapAngle(a: Float): Float {
    val nearest = (a / 45f).roundToInt() * 45f
    return if (abs(a - nearest) < 4f) nearest else a
}

@Composable
private fun Handle(
    icon: ImageVector,
    description: String,
    cx: Float,
    cy: Float,
    color: Color,
    size: Dp = 30.dp,
    gesture: @Composable () -> Modifier,
) {
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
            .size(size)
            .shadow(6.dp, CircleShape, spotColor = color)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .then(gesture()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** One-finger resize + rotate, like the corner handle in photo editors. */
@Composable
private fun ResizeRotateHandle(
    note: TextNote,
    hx: Float,
    hy: Float,
    ox: Float,
    oy: Float,
    wPx: Float,
    hPx: Float,
    vm: MainViewModel,
) {
    val latestNote by rememberUpdatedState(note)
    val handlePos by rememberUpdatedState(Offset(hx, hy))
    val origin by rememberUpdatedState(Offset(ox, oy))
    Handle(Icons.Filled.OpenInFull, "Resize and rotate", hx, hy, NeonOrange, size = 34.dp) {
        Modifier.pointerInput(note.id, wPx, hPx) {
            var start = Offset.Zero
            var cur = Offset.Zero
            var center = Offset.Zero
            var s0 = 1f
            var r0 = 0f
            detectDragGestures(
                onDragStart = {
                    val n = latestNote
                    center = Offset(origin.x + n.x * wPx, origin.y + n.y * hPx)
                    start = handlePos
                    cur = start
                    s0 = n.scale
                    r0 = n.rotation
                    vm.beginGesture()
                },
                onDragEnd = { vm.endGesture() },
                onDragCancel = { vm.endGesture() },
            ) { change, drag ->
                change.consume()
                cur += drag
                val v0 = start - center
                val v = cur - center
                val len0 = hypot(v0.x, v0.y).coerceAtLeast(1f)
                val len = hypot(v.x, v.y)
                val angle = Math.toDegrees((atan2(v.y, v.x) - atan2(v0.y, v0.x)).toDouble()).toFloat()
                val n = latestNote
                vm.setTransform(n.id, n.x, n.y, s0 * len / len0, snapAngle(r0 + angle))
            }
        }
    }
}

@Composable
private fun FloatingToolbar(
    note: TextNote,
    anchorTop: Float,
    anchorBottom: Float,
    anchorCenterX: Float,
    maxWidthPx: Float,
    maxHeightPx: Float,
    vm: MainViewModel,
    onEdit: () -> Unit,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { 26.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val above = anchorTop - gap - size.height
    val y = when {
        above >= margin -> above
        anchorBottom + gap + size.height <= maxHeightPx - margin -> anchorBottom + gap
        else -> margin
    }
    val x = (anchorCenterX - size.width / 2f).coerceIn(margin, (maxWidthPx - size.width - margin).coerceAtLeast(margin))
    val maxW = with(density) { (maxWidthPx - margin * 2).toDp() }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { size = it },
    ) {
        Row(
            Modifier
                .widthIn(max = maxW)
                .shadow(14.dp, RoundedCornerShape(20.dp), spotColor = NeonOrange)
                .clip(RoundedCornerShape(20.dp))
                .background(Glass)
                .border(1.dp, Brush.horizontalGradient(listOf(Color(0x88FF7A1A), Color(0x33FF7A1A))), RoundedCornerShape(20.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Filled.Edit, "Edit") { onEdit() }
            ToolButton(Icons.Filled.Mic, "Add speech", highlight = true) { vm.dictateInto(note.id) }
            if (!note.locked) {
                ToolButton(Icons.Filled.TextDecrease, "Smaller") { vm.scaleNote(note.id, 1f / 1.15f) }
                ToolButton(Icons.Filled.TextIncrease, "Bigger") { vm.scaleNote(note.id, 1.15f) }
                ToolButton(Icons.Filled.AlignHorizontalCenter, "Center") { vm.centerNote(note.id) }
                ToolButton(Icons.Filled.RestartAlt, "Straighten") { vm.resetTransform(note.id) }
            }
            ToolButton(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateNote(note.id) }
            ToolButton(Icons.Filled.FlipToFront, "Front") { vm.bringToFront(note.id) }
            ToolButton(Icons.Filled.FlipToBack, "Back") { vm.sendToBack(note.id) }
            ToolButton(if (note.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (note.locked) "Unlock" else "Lock") {
                vm.toggleLock(note.id)
            }
            ToolButton(Icons.Filled.Delete, "Delete", danger = true) { vm.deleteNote(note.id) }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> Color(0xFFFF6B6B)
        highlight -> Color.Black
        else -> Color.White
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(if (highlight) Modifier.background(OrangeGradient, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(label, color = Color(0xFFCFCFD6), fontSize = 9.5.sp, maxLines = 1)
    }
}

@Composable
private fun QuickChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .border(1.dp, Color(0x66FF7A1A), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NeonOrange, modifier = Modifier.size(16.dp))
        Text(" $label", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyEditor() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Box(
            Modifier.size(88.dp).background(Graphite, CircleShape).border(1.dp, Color(0x55FF7A1A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Image, null, tint = NeonOrange, modifier = Modifier.size(40.dp))
        }
        Text(
            "Pick an image from the reel, then tap the mic and speak.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
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
