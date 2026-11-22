package com.imagenote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imagenote.app.data.BackgroundStyle
import com.imagenote.app.data.FontOption
import com.imagenote.app.data.Fonts
import com.imagenote.app.data.NoteStyle
import com.imagenote.app.data.TextAlign
import com.imagenote.app.data.TextNote
import kotlin.math.roundToInt

enum class PanelTab(val label: String) { STYLE("Style"), NOTES("Notes"), SETTINGS("Settings") }

private val Swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFE14D, 0xFFFF8A3D, 0xFFFF4D6D, 0xFFFF7EDB,
    0xFFB388FF, 0xFF5AA9FF, 0xFF4DE1C1, 0xFF7CF06B, 0xFF9E9E9E, 0xFF5D4037,
).map { it.toInt() }

fun fontFamilyFor(opt: FontOption): FontFamily = when {
    opt.resId != null -> FontFamily(Font(opt.resId))
    opt.systemFamily == "serif" -> FontFamily.Serif
    opt.systemFamily == "monospace" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

// ---------------------------------------------------------------- Style

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StylePanel(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val style = state.editingStyle
    val editingNote = state.selectedNote != null
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (editingNote) "● Editing the selected note" else "Style for new notes",
            style = MaterialTheme.typography.titleMedium,
            color = if (editingNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (editingNote) "Tap empty space on the image to go back to the default style."
            else "Tap a note on the image to restyle just that note.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("Font")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(Fonts.all, key = { it.id }) { opt ->
                val sel = opt.id == style.fontId
                Box(
                    Modifier
                        .clip16()
                        .background(
                            if (sel) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable { vm.updateStyle { it.copy(fontId = opt.id) }; vm.styleEditFinished() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        opt.label,
                        fontFamily = fontFamilyFor(opt),
                        fontStyle = if (style.slant > 0.05f) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 20.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        LabeledSlider("Size", style.size, 15f..250f, "${style.size.roundToInt()}", vm) { v -> copy(size = v) }
        LabeledSlider("Italic slant", style.slant, 0f..0.6f, "${(style.slant * 100).roundToInt()}%", vm) { v -> copy(slant = v) }
        LabeledSlider("Outline", style.outlineWidth, 0f..0.3f, "${(style.outlineWidth * 100).roundToInt()}%", vm) { v -> copy(outlineWidth = v) }
        LabeledSlider("Opacity", style.opacity, 0.1f..1f, "${(style.opacity * 100).roundToInt()}%", vm) { v -> copy(opacity = v) }

        Section("Text colour")
        ColorRow(style.textColor) { c -> vm.updateStyle { it.copy(textColor = c) }; vm.styleEditFinished() }
        Section("Outline colour")
        ColorRow(style.outlineColor) { c -> vm.updateStyle { it.copy(outlineColor = c) }; vm.styleEditFinished() }

        Section("Style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style.bold, onClick = { vm.updateStyle { it.copy(bold = !it.bold) }; vm.styleEditFinished() },
                label = { Text("Bold") },
            )
            FilterChip(
                selected = style.allCaps, onClick = { vm.updateStyle { it.copy(allCaps = !it.allCaps) }; vm.styleEditFinished() },
                label = { Text("ALL CAPS") },
            )
            FilterChip(
                selected = style.slant > 0f,
                onClick = { vm.updateStyle { it.copy(slant = if (it.slant > 0f) 0f else 0.18f) }; vm.styleEditFinished() },
                label = { Text("Italic", fontStyle = FontStyle.Italic) },
            )
        }

        Section("Alignment")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TextAlign.entries.forEachIndexed { i, a ->
                SegmentedButton(
                    selected = style.align == a,
                    onClick = { vm.updateStyle { it.copy(align = a) }; vm.styleEditFinished() },
                    shape = SegmentedButtonDefaults.itemShape(i, TextAlign.entries.size),
                    icon = {},
                ) {
                    Icon(
                        when (a) {
                            TextAlign.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                            TextAlign.CENTER -> Icons.Filled.FormatAlignCenter
                            TextAlign.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                        }, a.name
                    )
                }
            }
        }

        Section("Effect")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundStyle.entries.forEach { b ->
                FilterChip(
                    selected = style.background == b,
                    onClick = {
                        vm.updateStyle {
                            val needsColour = (b == BackgroundStyle.GLOW || b == BackgroundStyle.HIGHLIGHT) &&
                                it.backgroundColor == 0xFF000000.toInt()
                            it.copy(background = b, backgroundColor = if (needsColour) 0xFFFF7A1A.toInt() else it.backgroundColor)
                        }
                        vm.styleEditFinished()
                    },
                    label = { Text(b.label) },
                )
            }
        }
        if (style.background != BackgroundStyle.NONE && style.background != BackgroundStyle.SHADOW) {
            Text(
                if (style.background == BackgroundStyle.GLOW) "Glow colour" else "Box colour",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
            ColorRow(style.backgroundColor) { c -> vm.updateStyle { it.copy(backgroundColor = c) }; vm.styleEditFinished() }
        }

        Section("Spacing & wrapping")
        LabeledSlider("Letter spacing", style.letterSpacing, -0.05f..0.4f, "%.2f".format(style.letterSpacing), vm) { v -> copy(letterSpacing = v) }
        LabeledSlider("Line spacing", style.lineSpacing, 0.6f..1.8f, "%.1f×".format(style.lineSpacing), vm) { v -> copy(lineSpacing = v) }
        LabeledSlider("Wrap width", style.maxWidth, 0.25f..1f, "${(style.maxWidth * 100).roundToInt()}%", vm) { v -> copy(maxWidth = v) }

        Section("Presets")
        if (state.presets.isEmpty()) {
            Text(
                "Save styles you like and re-use them with one tap. Long-press a preset to delete it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(state.presets) { p -> PresetChip(p, onClick = { vm.applyPreset(p) }, onLongClick = { vm.deletePreset(p) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = vm::savePreset, label = { Text("Save as preset") })
            if (editingNote) {
                AssistChip(onClick = vm::makeDefaultStyle, label = { Text("Use for new notes") })
            }
            if (state.notes.size > 1) {
                AssistChip(onClick = vm::applyStyleToAll, label = { Text("Apply to all notes") })
            }
            AssistChip(onClick = { vm.resetStyle(); vm.styleEditFinished() }, label = { Text("Reset") })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(style: NoteStyle, onClick: () -> Unit, onLongClick: () -> Unit) {
    val opt = Fonts.option(style.fontId)
    Box(
        Modifier
            .clip16()
            .background(Color(0xFF3A3A44))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "Note",
            fontFamily = fontFamilyFor(opt),
            fontStyle = if (style.slant > 0.05f) FontStyle.Italic else FontStyle.Normal,
            fontSize = 22.sp,
            style = TextStyle(
                color = Color(style.textColor),
                shadow = androidx.compose.ui.graphics.Shadow(Color(style.outlineColor), blurRadius = 4f),
            ),
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    vm: MainViewModel,
    apply: NoteStyle.(Float) -> NoteStyle,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { v -> vm.updateStyle { it.apply(v) } },
            onValueChangeFinished = vm::styleEditFinished,
            valueRange = range,
        )
    }
}

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) ColorPickerDialog(selected, onDismiss = { custom = false }) { onPick(it); custom = false }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), CircleShape)
                    .border(if (selected !in Swatches) 3.dp else 1.dp, if (selected !in Swatches) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF), CircleShape)
                    .clickable { custom = true },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Color.White, fontSize = 18.sp) }
        }
        items(Swatches) { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(34.dp)
                    .background(Color(c), CircleShape)
                    .border(
                        if (isSel) 3.dp else 1.dp,
                        if (isSel) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF),
                        CircleShape,
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

private fun Modifier.clip16() = this.clip(RoundedCornerShape(16.dp))

@Composable
private fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(color))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                )
                Text("Hue", modifier = Modifier.padding(top = 12.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                        Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    )
                )
                Slider(hue, { hue = it }, valueRange = 0f..360f)
                Text("Saturation")
                Slider(sat, { sat = it }, valueRange = 0f..1f)
                Text("Brightness")
                Slider(value, { value = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Notes

@Composable
fun NotesPanel(state: UiState, vm: MainViewModel, onEdit: (TextNote) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notes on this image", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.notes.isNotEmpty()) {
                TextButton(onClick = { copy(context, state.notes.joinToString("\n") { it.text }); vm.toast("Copied all notes") }) {
                    Text("Copy all")
                }
            }
        }
        if (state.notes.isEmpty()) {
            Text(
                "No notes yet. Tap the mic, speak, then pause — your words appear on the image.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.notes.forEachIndexed { i, note ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectNote(note.id) }
                    .background(
                        if (note.id == state.selectedNoteId) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else Color.Transparent
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    note.text,
                    fontFamily = fontFamilyFor(Fonts.option(note.style.fontId)),
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = { copy(context, note.text); vm.toast("Copied") }) {
                    Icon(Icons.Outlined.ContentCopy, "Copy")
                }
                IconButton(onClick = { onEdit(note) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { vm.deleteNote(note.id) }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
        if (state.notes.isNotEmpty()) {
            TextButton(onClick = vm::clearNotes, modifier = Modifier.padding(top = 8.dp)) { Text("Remove all notes") }
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("ImageNote", text))
}

// ---------------------------------------------------------------- Settings

private val Languages = listOf(
    "" to "Device language",
    "en-US" to "English (US)", "en-GB" to "English (UK)", "en-AU" to "English (Australia)",
    "en-IN" to "English (India)", "es-ES" to "Español", "es-US" to "Español (US)",
    "fr-FR" to "Français", "de-DE" to "Deutsch", "it-IT" to "Italiano", "pt-BR" to "Português (BR)",
    "nl-NL" to "Nederlands", "pl-PL" to "Polski", "hi-IN" to "हिन्दी", "ja-JP" to "日本語",
    "ko-KR" to "한국어", "zh-CN" to "中文 (简体)", "ar-SA" to "العربية", "ru-RU" to "Русский",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val s = state.settings
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Voice", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Finish after silence of")
                Text("%.1f s".format(s.silenceMillis / 1000f), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = s.silenceMillis / 1000f,
                onValueChange = { v -> vm.updateSettings { it.copy(silenceMillis = (v * 2).roundToInt() * 500L) } },
                valueRange = 1f..6f,
                steps = 9,
            )
        }
        SwitchRow("Prefer on-device recognition", "Faster and works offline on the Fold 7", s.preferOnDevice) {
            vm.updateSettings { st -> st.copy(preferOnDevice = it) }
        }
        SwitchRow("Capitalise first word", null, s.autoCapitalize) { vm.updateSettings { st -> st.copy(autoCapitalize = it) } }
        SwitchRow("Stack new notes", "Place each new note below the last one", s.stackNotes) {
            vm.updateSettings { st -> st.copy(stackNotes = it) }
        }
        SwitchRow("Haptic feedback", null, s.haptics) { vm.updateSettings { st -> st.copy(haptics = it) } }

        Text("Language", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Languages.forEach { (tag, name) ->
                FilterChip(
                    selected = s.language == tag,
                    onClick = { vm.updateSettings { it.copy(language = tag) } },
                    label = { Text(name) },
                )
            }
        }

        Text("Saving", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        SwitchRow("Delete original after saving", "The annotated copy replaces the original photo", s.deleteOriginal) {
            vm.updateSettings { st -> st.copy(deleteOriginal = it) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("JPEG quality")
            Text("${s.jpegQuality}", color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = s.jpegQuality.toFloat(),
            onValueChange = { v -> vm.updateSettings { it.copy(jpegQuality = v.roundToInt()) } },
            valueRange = 70f..100f,
        )
        Text(
            "Saved copies go to Pictures/ImageNote and include your spoken text in the photo's description. With \"Delete original\" off, originals are never changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- Edit dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteDialog(note: TextNote, onDismiss: () -> Unit, title: String = "Edit note", onSave: (String) -> Unit) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = TextStyle(fontFamily = fontFamilyFor(Fonts.option(note.style.fontId)), fontSize = 22.sp),
                minLines = 2,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

