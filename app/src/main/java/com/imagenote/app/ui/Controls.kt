package com.imagenote.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.imagenote.app.data.GalleryImage
import com.imagenote.app.speech.VoiceState
import com.imagenote.app.ui.theme.Graphite
import com.imagenote.app.ui.theme.NeonAmber
import com.imagenote.app.ui.theme.NeonOrange
import com.imagenote.app.ui.theme.NeonOrangeDeep
import com.imagenote.app.ui.theme.OrangeGradient
import com.imagenote.app.ui.theme.Slate

/** Side-scrolling reel of the most recent gallery images. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImageReel(
    images: List<GalleryImage>,
    current: android.net.Uri?,
    annotated: Set<String>,
    itemSize: Dp,
    onPick: (GalleryImage) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    isCapture: (GalleryImage) -> Boolean = { false },
    onLongPress: (GalleryImage) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // When a new photo arrives at the front, scroll back to show it.
    LaunchedEffect(images.firstOrNull()?.uri) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    val shape = RoundedCornerShape(16.dp)
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "browse") {
            Box(
                Modifier
                    .size(itemSize)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Slate, Graphite)))
                    .border(1.dp, Color(0x55FF7A1A), shape)
                    .clickable(onClick = onBrowse),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = NeonOrange)
                    Text("Browse", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        items(images, key = { it.uri.toString() }) { img ->
            val selected = img.uri == current
            val s by animateFloatAsState(if (selected) 1f else 0.9f, label = "thumb")
            Box(
                Modifier
                    .size(itemSize)
                    .scale(s)
                    .then(
                        if (selected) Modifier
                            .shadow(14.dp, shape, ambientColor = NeonOrange, spotColor = NeonOrange)
                            .border(3.dp, OrangeGradient, shape)
                        else Modifier
                    )
                    .clip(shape)
                    .combinedClickable(onClick = { onPick(img) }, onLongClick = { onLongPress(img) })
                    .semantics { contentDescription = img.displayName ?: "Image" },
            ) {
                AsyncImage(
                    model = img.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(if (selected) 1f else 0.82f),
                )
                if (isCapture(img)) {
                    Text(
                        "NOT SAVED",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(OrangeGradient, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (img.uri.toString() in annotated) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .size(20.dp)
                            .shadow(4.dp, CircleShape, spotColor = NeonOrange)
                            .background(OrangeGradient, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

/** Big glowing record button. Pulses with the microphone level while listening. */
@Composable
fun MicButton(voice: VoiceState, onClick: () -> Unit, size: Dp = 78.dp) {
    val listening = voice is VoiceState.Listening
    val level = (voice as? VoiceState.Listening)?.level ?: 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.5f)) {
        if (listening) PulseRing(level, size)
        Box(
            Modifier
                .size(size)
                .shadow(if (listening) 26.dp else 18.dp, CircleShape, ambientColor = NeonOrange, spotColor = NeonOrange)
                .background(
                    if (listening) Brush.linearGradient(listOf(Color(0xFFFFD27A), NeonOrange, NeonOrangeDeep))
                    else OrangeGradient,
                    CircleShape,
                )
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x00FFFFFF))), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = if (listening) "Stop listening" else "Speak a note" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                null,
                tint = Color(0xFF140800),
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/** Only composed while listening, so the animation never runs (or drains battery) when idle. */
@Composable
private fun PulseRing(level: Float, size: Dp) {
    val animatedLevel by animateFloatAsState(level, tween(90), label = "level")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "breathe"
    )
    Box(
        Modifier
            .size(size)
            .scale(breathe + animatedLevel * 0.35f)
            .background(Brush.radialGradient(listOf(Color(0x66FF7A1A), Color(0x00FF7A1A))), CircleShape)
    )
    Box(
        Modifier
            .size(size)
            .scale(1.05f + animatedLevel * 0.2f)
            .border(2.dp, NeonAmber.copy(alpha = 0.7f), CircleShape)
    )
}

/** Undo · Redo · [Mic] · Save · Share */
@Composable
fun ControlBar(
    state: UiState,
    voice: VoiceState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMic: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    micSize: Dp = 78.dp,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIcon(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = state.canUndo, onClick = onUndo)
        LabeledIcon(Icons.AutoMirrored.Outlined.Redo, "Redo", enabled = state.canRedo, onClick = onRedo)
        MicButton(voice, onMic, micSize)
        LabeledIcon(Icons.Outlined.Download, "Save", enabled = state.notes.isNotEmpty() && !state.saving, busy = state.saving, onClick = onSave)
        LabeledIcon(Icons.Outlined.Share, "Share", enabled = state.notes.isNotEmpty() && !state.saving, onClick = onShare)
    }
}

@Composable
fun LabeledIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled || busy) 1f else 0.35f),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF26262E), Color(0xFF17171C))))
                .border(1.dp, if (enabled) Color(0x66FF7A1A) else Color(0x22FFFFFF), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonOrange)
            else Icon(icon, label, tint = if (enabled) NeonOrange else Color.White)
        }
        Text(label, fontSize = 11.sp, color = Color(0xFFBDBDC6), modifier = Modifier.padding(top = 3.dp))
    }
}
