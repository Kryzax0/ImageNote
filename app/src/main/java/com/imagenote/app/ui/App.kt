package com.imagenote.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.imagenote.app.ui.theme.BrandTitle
import com.imagenote.app.ui.theme.Graphite
import com.imagenote.app.ui.theme.NeonOrange
import com.imagenote.app.ui.theme.OrangeGradient
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.imagenote.app.data.TextNote
import com.imagenote.app.speech.VoiceState

/**
 * Hinge information from Jetpack WindowManager, in window pixels.
 * [horizontalHinge] = Flex mode (phone half-folded on a table, top half shows the image).
 * [verticalHinge] = half-folded like a book.
 */
data class FoldPosture(
    val horizontalHinge: android.graphics.Rect? = null,
    val verticalHinge: android.graphics.Rect? = null,
)

private fun imagePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageNoteApp(vm: MainViewModel, posture: FoldPosture) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val state by vm.ui.collectAsStateWithLifecycle()
    val voice by vm.voiceState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var hasGallery by remember { mutableStateOf(hasAny(context, imagePermissions())) }
    var askedGallery by rememberSaveable { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf<PanelTab?>(null) }
    var editing by remember { mutableStateOf<TextNote?>(null) }
    var typing by remember { mutableStateOf<Pair<Float?, Float?>?>(null) }
    var discarding by remember { mutableStateOf<Uri?>(null) }

    val galleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasGallery = it.values.any { granted -> granted }
        askedGallery = true
        vm.onGalleryPermission(hasGallery)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleVoice() else vm.toast("Microphone permission is needed for voice notes")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::openExternal)
    }
    val browse = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(Unit) {
        if (hasGallery) vm.onGalleryPermission(true)
        else if (!askedGallery) galleryPermission.launch(imagePermissions())
    }

    // Messages
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    LaunchedEffect(voice) {
        (voice as? VoiceState.Error)?.let { snackbar.showSnackbar(it.message); vm.clearVoiceError() }
    }

    // Haptics + keep the screen awake while listening.
    val listening = voice is VoiceState.Listening
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(listening) {
        if (!firstRun[0] && state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        firstRun[0] = false
    }
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = state.selectedNoteId != null) { vm.selectNote(null) }

    val onMic = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.toggleVoice()
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val onShare = {
        vm.saveOrReuse { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, vm.ui.value.notes.joinToString("\n") { it.text })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share image"))
        }
    }
    val onSave = { vm.saveOrReuse(null) }
    val transparent = rememberTransparentMode(vm)

    var rootOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val density = LocalDensity.current

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.positionInWindow() }
        ) {
            if (!hasGallery && state.images.isEmpty()) {
                PermissionScreen(
                    onAllow = { galleryPermission.launch(imagePermissions()) },
                    onBrowse = browse,
                )
            } else {
                val fullWidth = maxWidth
                val wide = fullWidth >= 600.dp
                val hTop = posture.horizontalHinge?.let { with(density) { (it.top - rootOffset.y).toDp() } }
                val hHeight = posture.horizontalHinge?.let { with(density) { it.height().toDp() } }
                val vLeft = posture.verticalHinge?.let { with(density) { (it.left - rootOffset.x).toDp() } }
                val vWidth = posture.verticalHinge?.let { with(density) { it.width().toDp() } }

                val reel: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { size ->
                    ImageReel(
                        images = state.images,
                        current = state.current,
                        annotated = state.annotated,
                        itemSize = size,
                        onPick = { vm.select(it.uri) },
                        onBrowse = browse,
                        isCapture = { vm.isCapture(it.uri) },
                        onLongPress = { if (vm.isCapture(it.uri)) discarding = it.uri },
                    )
                }
                val editor: @Composable (Modifier) -> Unit = { m ->
                    Editor(
                        state, voice, vm,
                        onEditNote = { editing = it },
                        onTypeNote = { x, y -> typing = x to y },
                        onTransparent = transparent.toggle,
                        modifier = m,
                    )
                }

                when {
                    // ---------- Flex mode: image above the fold, controls below ----------
                    hTop != null && hTop > 120.dp -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(hTop).safeDrawingPadding()) { editor(Modifier) }
                        Spacer(Modifier.height(hHeight ?: 0.dp))
                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            reel(if (wide) 96.dp else 72.dp)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                BrandTitleText(Modifier.weight(1f))
                                TopActions(onStyle = { sheetTab = PanelTab.STYLE }, onNotes = { sheetTab = PanelTab.NOTES }, onSettings = { sheetTab = PanelTab.SETTINGS })
                            }
                            ControlBar(state, voice, vm::undo, vm::redo, onMic, onSave, onShare, micSize = 96.dp)
                        }
                    }

                    // ---------- Unfolded inner screen: editor + permanent side panel ----------
                    wide -> Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                        val leftMod = if (vLeft != null && vLeft > 300.dp && fullWidth - vLeft > 280.dp)
                            Modifier.width(vLeft).fillMaxHeight()
                        else Modifier.weight(1f).fillMaxHeight()
                        Column(leftMod) {
                            editor(Modifier.weight(1f).padding(8.dp))
                            reel(104.dp)
                            ControlBar(state, voice, vm::undo, vm::redo, onMic, onSave, onShare, Modifier.padding(bottom = 8.dp))
                        }
                        if (vLeft != null && vWidth != null) Spacer(Modifier.width(vWidth))
                        val sideMod = if (vLeft != null && vLeft > 300.dp && fullWidth - vLeft > 280.dp)
                            Modifier.weight(1f)
                        else Modifier.width((fullWidth * 0.36f).coerceIn(320.dp, 440.dp))
                        SidePanel(state, vm, onEdit = { editing = it }, modifier = sideMod.fillMaxHeight())
                    }

                    // ---------- Cover screen / folded ----------
                    else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BrandTitleText(Modifier.weight(1f))
                            TopActions(onStyle = { sheetTab = PanelTab.STYLE }, onNotes = { sheetTab = PanelTab.NOTES }, onSettings = { sheetTab = PanelTab.SETTINGS })
                        }
                        editor(Modifier.weight(1f))
                        reel(76.dp)
                        ControlBar(state, voice, vm::undo, vm::redo, onMic, onSave, onShare, Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 120.dp)) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF1E1E24),
                    contentColor = Color.White,
                    actionColor = NeonOrange,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0x66FF7A1A), RoundedCornerShape(16.dp)),
                )
            }
        }
    }

    sheetTab?.let { tab ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { sheetTab = null; vm.styleEditFinished() },
            sheetState = sheetState,
            containerColor = Graphite,
            scrimColor = Color.Black.copy(alpha = 0.15f),
        ) {
            TabbedPanels(state, vm, initial = tab, onEdit = { editing = it }, modifier = Modifier.fillMaxHeight(0.62f))
        }
    }

    typing?.let { (x, y) ->
        EditNoteDialog(
            note = remember { TextNote(text = "") },
            title = "New note",
            onDismiss = { typing = null },
            onSave = { vm.addTypedNote(it, x, y); typing = null },
        )
    }

    if (transparent.showIntro) TransparentIntroDialog(transparent)

    discarding?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { discarding = null },
            containerColor = Graphite,
            title = { Text("Discard this capture?") },
            text = { Text("It hasn't been saved to your gallery. Its notes will be lost.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.discardCapture(uri); discarding = null }) {
                    Text("Discard", color = NeonOrange)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { discarding = null }) { Text("Keep") } },
        )
    }

    editing?.let { note ->
        EditNoteDialog(
            note = note,
            onDismiss = { editing = null },
            onSave = { vm.updateNoteText(note.id, it); editing = null },
        )
    }
}

@Composable
private fun SidePanel(state: UiState, vm: MainViewModel, onEdit: (TextNote) -> Unit, modifier: Modifier) {
    Surface(
        color = Graphite,
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        modifier = modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(Color(0x66FF7A1A), Color(0x11FF7A1A))),
            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        ),
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandTitleText(Modifier.weight(1f))
                state.lastSaved?.let { SavedDragChip(it) }
            }
            TabbedPanels(state, vm, initial = PanelTab.STYLE, onEdit = onEdit, modifier = Modifier.weight(1f))
        }
    }
}

/** Long-press and drag the saved image straight into another app in split screen / pop-up view. */
@Composable
private fun SavedDragChip(uri: Uri) {
    val view = LocalView.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(uri) { detectDragGesturesAfterLongPress(onDragStart = { startImageDrag(view, uri) }) { _, _ -> } }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
        )
        Text(
            "Saved · hold to drag",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun TabbedPanels(
    state: UiState,
    vm: MainViewModel,
    initial: PanelTab,
    onEdit: (TextNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(initial) }
    Column(modifier) {
        TabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = NeonOrange,
            indicator = { pos ->
                androidx.compose.material3.TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(pos[tab.ordinal]),
                    width = 36.dp, color = NeonOrange,
                )
            },
            divider = {},
        ) {
            PanelTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    selectedContentColor = NeonOrange,
                    unselectedContentColor = Color(0xFF9A9AA6),
                    text = { Text(if (t == PanelTab.NOTES && state.notes.isNotEmpty()) "Notes (${state.notes.size})" else t.label) },
                )
            }
        }
        when (tab) {
            PanelTab.STYLE -> StylePanel(state, vm)
            PanelTab.NOTES -> NotesPanel(state, vm, onEdit)
            PanelTab.SETTINGS -> SettingsPanel(state, vm)
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onBrowse: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ImageNote", style = BrandTitle.copy(fontSize = 44.sp))
        Text("Voice notes on your photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(
            "ImageNote shows your recent photos in a reel so you can pick one, speak, and have your words written on it.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Box(
            Modifier
                .shadow(16.dp, RoundedCornerShape(50), spotColor = NeonOrange)
                .background(OrangeGradient, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAllow)
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) { Text("Allow access to photos", color = Color.Black, style = MaterialTheme.typography.labelLarge) }
        OutlinedButton(onClick = onBrowse, modifier = Modifier.padding(top = 8.dp)) { Text("Pick a single photo instead") }
    }
}

@Composable
private fun BrandTitleText(modifier: Modifier = Modifier) {
    Text("ImageNote", style = BrandTitle, modifier = modifier)
}

@Composable
private fun TopActions(onStyle: () -> Unit, onNotes: () -> Unit, onSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = onStyle) { Icon(Icons.Outlined.Palette, "Style", tint = NeonOrange) }
        IconButton(onClick = onNotes) { Icon(Icons.AutoMirrored.Outlined.Notes, "Notes", tint = Color.White) }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White) }
    }
}

private fun hasAny(context: android.content.Context, perms: Array<String>) =
    perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
