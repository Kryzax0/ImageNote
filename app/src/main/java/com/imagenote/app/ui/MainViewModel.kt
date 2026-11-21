package com.imagenote.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imagenote.app.data.AppSettings
import com.imagenote.app.data.GalleryImage
import com.imagenote.app.data.GalleryRepository
import com.imagenote.app.data.NoteStore
import com.imagenote.app.data.NoteStyle
import com.imagenote.app.data.SettingsStore
import com.imagenote.app.data.TextNote
import com.imagenote.app.render.Exporter
import com.imagenote.app.render.NoteRenderer
import com.imagenote.app.speech.VoiceCapture
import com.imagenote.app.speech.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val images: List<GalleryImage> = emptyList(),
    val galleryLoaded: Boolean = false,
    val current: Uri? = null,
    /** width / height of the current image, once known. */
    val aspect: Float = 0f,
    val notes: List<TextNote> = emptyList(),
    val selectedNoteId: String? = null,
    /** While dictating "more" into an existing note, its id. */
    val appendTargetId: String? = null,
    val defaultStyle: NoteStyle = NoteStyle(),
    val presets: List<NoteStyle> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val annotated: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saving: Boolean = false,
    val lastSaved: Uri? = null,
    val message: String? = null,
) {
    val selectedNote: TextNote? get() = notes.firstOrNull { it.id == selectedNoteId }
    /** The style that the style panel edits: the selected note's, or the default for new notes. */
    val editingStyle: NoteStyle get() = selectedNote?.style ?: defaultStyle
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val gallery = GalleryRepository(app)
    private val noteStore = NoteStore(app)
    private val settingsStore = SettingsStore(app)
    val voice = VoiceCapture(app, viewModelScope)
    val voiceState: StateFlow<VoiceState> get() = voice.state

    private val _ui = MutableStateFlow(
        UiState(
            defaultStyle = settingsStore.loadDefaultStyle(),
            presets = settingsStore.loadPresets(),
            settings = settingsStore.loadSettings(),
            annotated = noteStore.annotatedUris(),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val undo = ArrayDeque<List<TextNote>>()
    private val redo = ArrayDeque<List<TextNote>>()
    private val extraImages = mutableListOf<GalleryImage>()
    private var watchingGallery: Job? = null
    private var persistJob: Job? = null

    init {
        // Clear the "dictating into this note" marker whenever listening ends (incl. errors / silence).
        viewModelScope.launch {
            voice.state.collect { st ->
                if (st !is VoiceState.Listening && _ui.value.appendTargetId != null) {
                    delay(50) // let the result callback use it first
                    _ui.update { it.copy(appendTargetId = null) }
                }
            }
        }
    }

    // ---------- Gallery ----------

    fun onGalleryPermission(granted: Boolean) {
        if (!granted) {
            _ui.update { it.copy(galleryLoaded = true) }
            return
        }
        refreshGallery(selectFirst = true)
        if (watchingGallery == null) {
            watchingGallery = viewModelScope.launch {
                gallery.changes().collect {
                    delay(400) // let the writer finish
                    refreshGallery(selectFirst = false)
                }
            }
        }
    }

    fun refreshGallery(selectFirst: Boolean) = viewModelScope.launch {
        val imgs = gallery.recentImages()
        val merged = (extraImages.filter { e -> imgs.none { it.uri == e.uri } } + imgs)
        _ui.update { it.copy(images = merged, galleryLoaded = true) }
        if (selectFirst && _ui.value.current == null) merged.firstOrNull()?.let { select(it.uri) }
    }

    /** Images from the photo picker, a share intent or a drag & drop. */
    fun openExternal(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (_ui.value.images.none { it.uri == uri }) {
            val img = GalleryImage(uri, -1, System.currentTimeMillis() / 1000, 0, 0, null, null)
            extraImages.add(0, img)
            _ui.update { it.copy(images = listOf(img) + it.images) }
        }
        select(uri)
    }

    fun select(uri: Uri) {
        if (_ui.value.current == uri) return
        voice.cancel()
        undo.clear()
        redo.clear()
        _ui.update {
            it.copy(
                current = uri,
                aspect = 0f,
                notes = noteStore.notesFor(uri),
                selectedNoteId = null,
                appendTargetId = null,
                canUndo = false,
                canRedo = false,
                lastSaved = null,
            )
        }
    }

    fun onImageSize(uri: Uri, width: Int, height: Int) {
        if (uri == _ui.value.current && width > 0 && height > 0) {
            _ui.update { it.copy(aspect = width.toFloat() / height) }
        }
    }

    // ---------- Voice ----------

    fun toggleVoice() = startVoice(appendTo = null)

    /** Dictate more words onto the end of an existing note. */
    fun dictateInto(noteId: String) = startVoice(appendTo = noteId)

    private fun startVoice(appendTo: String?) {
        if (voice.isListening) {
            voice.stop()
            return
        }
        val uri = _ui.value.current ?: run { toast("Pick an image first"); return }
        _ui.update { it.copy(appendTargetId = appendTo) }
        voice.start(_ui.value.settings) { text ->
            val target = _ui.value.appendTargetId
            _ui.update { it.copy(appendTargetId = null) }
            if (_ui.value.current != uri) return@start
            val existing = target?.let { id -> _ui.value.notes.firstOrNull { it.id == id } }
            if (existing != null) {
                updateNoteText(existing.id, existing.text.trimEnd() + " " + text.replaceFirstChar { it.lowercase() })
            } else addNote(text)
        }
    }

    fun clearVoiceError() = voice.clearError()

    /** Where the next spoken note goes (also used for the live preview while speaking). */
    fun nextNotePosition(text: String = "Ag"): Pair<Float, Float> {
        val s = _ui.value
        if (!s.settings.stackNotes || s.notes.isEmpty()) return 0.5f to 0.14f
        val aspect = if (s.aspect > 0f) s.aspect else 1f
        val w = 1000f
        val h = w / aspect
        val ctx = getApplication<Application>()
        val last = s.notes.last()
        val lastHalf = NoteRenderer.heightFraction(ctx, last, w, h) / 2f
        val probe = TextNote(text = text, style = s.defaultStyle)
        val newHalf = NoteRenderer.heightFraction(ctx, probe, w, h) / 2f
        var y = last.y + lastHalf + newHalf
        if (y + newHalf > 0.98f) y = 0.14f
        return last.x.coerceIn(0.2f, 0.8f) to y
    }

    private fun addNote(text: String) {
        val (x, y) = nextNotePosition(text)
        val note = TextNote(text = text, x = x, y = y, style = _ui.value.defaultStyle)
        pushUndo()
        _ui.update { it.copy(notes = it.notes + note, selectedNoteId = note.id) }
        persist()
    }

    /** A typed note, e.g. from long-pressing the image. */
    fun addTypedNote(text: String, x: Float? = null, y: Float? = null) {
        if (text.isBlank()) return
        val (nx, ny) = if (x != null && y != null) x to y else nextNotePosition(text)
        val note = TextNote(text = text.trim(), x = nx, y = ny, style = _ui.value.defaultStyle)
        pushUndo()
        _ui.update { it.copy(notes = it.notes + note, selectedNoteId = note.id) }
        persist()
    }

    // ---------- Note editing ----------

    fun selectNote(id: String?) = _ui.update { it.copy(selectedNoteId = id) }

    fun beginGesture() = pushUndo()

    /** Absolute transform during a gesture. Not persisted until [endGesture]. */
    fun setTransform(id: String, x: Float, y: Float, scale: Float, rotation: Float) {
        _ui.update { s ->
            s.copy(notes = s.notes.map {
                if (it.id != id) it else it.copy(
                    x = x.coerceIn(-0.1f, 1.1f),
                    y = y.coerceIn(-0.1f, 1.1f),
                    scale = scale.coerceIn(0.15f, 8f),
                    rotation = normalizeAngle(rotation),
                )
            })
        }
    }

    fun endGesture() = persist()

    private fun editNote(id: String, change: (TextNote) -> TextNote) {
        pushUndo()
        _ui.update { s -> s.copy(notes = s.notes.map { if (it.id == id) change(it) else it }) }
        persist()
    }

    fun updateNoteText(id: String, text: String) {
        if (text.isBlank()) return deleteNote(id)
        editNote(id) { it.copy(text = text) }
    }

    fun deleteNote(id: String) {
        pushUndo()
        _ui.update { s -> s.copy(notes = s.notes.filterNot { it.id == id }, selectedNoteId = null) }
        persist()
    }

    fun duplicateNote(id: String) {
        val n = _ui.value.notes.firstOrNull { it.id == id } ?: return
        val copy = n.copy(id = UUID.randomUUID().toString(), x = (n.x + 0.04f).coerceAtMost(1f), y = (n.y + 0.05f).coerceAtMost(1f), locked = false)
        pushUndo()
        _ui.update { it.copy(notes = it.notes + copy, selectedNoteId = copy.id) }
        persist()
    }

    fun scaleNote(id: String, factor: Float) = editNote(id) { it.copy(scale = (it.scale * factor).coerceIn(0.15f, 8f)) }

    fun rotateNote(id: String, degrees: Float) = editNote(id) { it.copy(rotation = normalizeAngle(it.rotation + degrees)) }

    fun centerNote(id: String) = editNote(id) { it.copy(x = 0.5f) }

    fun toggleLock(id: String) = editNote(id) { it.copy(locked = !it.locked) }

    fun bringToFront(id: String) {
        val n = _ui.value.notes.firstOrNull { it.id == id } ?: return
        pushUndo()
        _ui.update { s -> s.copy(notes = s.notes.filterNot { it.id == id } + n) }
        persist()
    }

    fun sendToBack(id: String) {
        val n = _ui.value.notes.firstOrNull { it.id == id } ?: return
        pushUndo()
        _ui.update { s -> s.copy(notes = listOf(n) + s.notes.filterNot { it.id == id }) }
        persist()
    }

    fun resetTransform(id: String) = editNote(id) { it.copy(scale = 1f, rotation = 0f) }

    fun clearNotes() {
        if (_ui.value.notes.isEmpty()) return
        pushUndo()
        _ui.update { it.copy(notes = emptyList(), selectedNoteId = null) }
        persist()
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(_ui.value.notes)
        restore(prev)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_ui.value.notes)
        restore(next)
    }

    private fun restore(notes: List<TextNote>) {
        _ui.update { s ->
            s.copy(
                notes = notes,
                selectedNoteId = s.selectedNoteId?.takeIf { id -> notes.any { it.id == id } },
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
            )
        }
        persist()
    }

    // ---------- Styles ----------

    private var styleUndoArmed = true

    /** Edits the selected note's style, or the default style for new notes if none is selected. */
    fun updateStyle(transform: (NoteStyle) -> NoteStyle) {
        val s = _ui.value
        val sel = s.selectedNote
        if (sel != null) {
            if (styleUndoArmed) { pushUndo(); styleUndoArmed = false }
            _ui.update { st -> st.copy(notes = st.notes.map { if (it.id == sel.id) it.copy(style = transform(it.style)) else it }) }
            persist(debounce = true)
        } else {
            val ns = transform(s.defaultStyle)
            _ui.update { it.copy(defaultStyle = ns) }
            settingsStore.saveDefaultStyle(ns)
        }
    }

    /** Call when a slider drag ends so the next change is its own undo step. */
    fun styleEditFinished() { styleUndoArmed = true }

    fun applyStyleToAll() {
        val style = _ui.value.editingStyle
        pushUndo()
        _ui.update { s -> s.copy(notes = s.notes.map { it.copy(style = style) }) }
        persist()
        toast("Style applied to all notes")
    }

    fun makeDefaultStyle() {
        val style = _ui.value.editingStyle
        _ui.update { it.copy(defaultStyle = style) }
        settingsStore.saveDefaultStyle(style)
        toast("New notes will use this style")
    }

    fun resetStyle() = updateStyle { NoteStyle() }

    fun savePreset() {
        val style = _ui.value.editingStyle
        val list = (listOf(style) + _ui.value.presets.filterNot { it == style }).take(12)
        _ui.update { it.copy(presets = list) }
        settingsStore.savePresets(list)
        toast("Preset saved")
    }

    fun deletePreset(style: NoteStyle) {
        val list = _ui.value.presets.filterNot { it == style }
        _ui.update { it.copy(presets = list) }
        settingsStore.savePresets(list)
    }

    fun applyPreset(style: NoteStyle) = updateStyle { style }.also { styleEditFinished() }

    // ---------- Settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val ns = transform(_ui.value.settings)
        _ui.update { it.copy(settings = ns) }
        settingsStore.saveSettings(ns)
    }

    // ---------- Export ----------

    private var savedSnapshot: List<TextNote>? = null

    /** Saves a new copy, or re-uses the last saved copy if nothing changed since. */
    fun saveOrReuse(then: ((Uri) -> Unit)?) {
        val s = _ui.value
        val last = s.lastSaved
        if (last != null && savedSnapshot == s.notes) {
            if (then != null) then(last) else toast("Already saved to Pictures/ImageNote")
        } else save(then)
    }

    fun save(then: ((Uri) -> Unit)? = null) {
        val s = _ui.value
        val uri = s.current ?: return
        if (s.saving) return
        if (s.notes.isEmpty()) { toast("Add a voice note first"); return }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                Exporter.export(getApplication(), uri, s.notes, s.settings.jpegQuality)
            }.onSuccess { saved ->
                savedSnapshot = s.notes
                _ui.update { it.copy(saving = false, lastSaved = saved, message = "Saved to Pictures/ImageNote") }
                then?.invoke(saved)
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, message = "Couldn't save: ${e.message}") }
            }
        }
    }

    fun toast(msg: String) = _ui.update { it.copy(message = msg) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    // ---------- internals ----------

    private fun pushUndo() {
        undo.addLast(_ui.value.notes)
        while (undo.size > 50) undo.removeFirst()
        redo.clear()
        _ui.update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun persist(debounce: Boolean = false) {
        val s = _ui.value
        val uri = s.current ?: return
        val notes = s.notes
        _ui.update { st ->
            st.copy(annotated = if (notes.isEmpty()) st.annotated - uri.toString() else st.annotated + uri.toString())
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (debounce) delay(300)
            noteStore.save(uri, notes)
        }
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        return r
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
    }
}
