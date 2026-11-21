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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.imagenote.app.data.GalleryImage
import com.imagenote.app.speech.VoiceState
import com.imagenote.app.ui.theme.RecordRed

/** Side-scrolling reel of the most recent gallery images. */
@Composable
fun ImageReel(
    images: List<GalleryImage>,
    current: android.net.Uri?,
    annotated: Set<String>,
    itemSize: Dp,
    onPick: (GalleryImage) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // When a new photo arrives at the front, scroll back to show it.
    LaunchedEffect(images.firstOrNull()?.uri) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "browse") {
            Box(
                Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBrowse),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Text("Browse", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        items(images, key = { it.uri.toString() }) { img ->
            val selected = img.uri == current
            Box(
                Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clickable { onPick(img) }
                    .semantics { contentDescription = img.displayName ?: "Image" },
            ) {
                AsyncImage(
                    model = img.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                if (img.uri.toString() in annotated) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .size(18.dp)
                            .background(RecordRed, CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

/** Big record button. Pulses with the microphone level while listening. */
@Composable
fun MicButton(voice: VoiceState, onClick: () -> Unit, size: Dp = 76.dp) {
    val listening = voice is VoiceState.Listening
    val level = (voice as? VoiceState.Listening)?.level ?: 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.45f)) {
        if (listening) PulseRing(level, size)
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (listening) RecordRed else MaterialTheme.colorScheme.primary,
            contentColor = if (listening) Color.White else MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = if (listening) "Stop listening" else "Speak a note" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    null,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
    }
}

/** Only composed while listening, so the animation never runs (or drains battery) when idle. */
@Composable
private fun PulseRing(level: Float, size: Dp) {
    val animatedLevel by animateFloatAsState(level, tween(90), label = "level")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe"
    )
    Box(
        Modifier
            .size(size)
            .scale(breathe + animatedLevel * 0.35f)
            .background(RecordRed.copy(alpha = 0.28f), CircleShape)
    )
}

/** Undo · Style · [Mic] · Save · Share */
@Composable
fun ControlBar(
    state: UiState,
    voice: VoiceState,
    onUndo: () -> Unit,
    onStyle: (() -> Unit)?,
    onMic: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    micSize: Dp = 76.dp,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIcon("Undo", enabled = state.canUndo, onClick = onUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, null)
        }
        if (onStyle != null) {
            LabeledIcon("Style", onClick = onStyle) { Icon(Icons.Outlined.Palette, null) }
        }
        MicButton(voice, onMic, micSize)
        LabeledIcon("Save", enabled = state.notes.isNotEmpty() && !state.saving, onClick = onSave) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Download, null)
        }
        LabeledIcon("Share", enabled = state.notes.isNotEmpty() && !state.saving, onClick = onShare) {
            Icon(Icons.Outlined.Share, null)
        }
    }
}

@Composable
private fun LabeledIcon(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}
