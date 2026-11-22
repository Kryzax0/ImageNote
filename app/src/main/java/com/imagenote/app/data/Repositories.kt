package com.imagenote.app.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Reads recent images from MediaStore (Samsung Gallery, Camera, Screenshots, Downloads, …). */
class GalleryRepository(private val context: Context) {

    suspend fun recentImages(limit: Int = 400): List<GalleryImage> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        val result = ArrayList<GalleryImage>()
        val args = android.os.Bundle().apply {
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        runCatching {
            context.contentResolver.query(collection, projection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val nCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    result += GalleryImage(
                        uri = ContentUris.withAppendedId(collection, id),
                        id = id,
                        dateAdded = c.getLong(dateCol),
                        width = c.getInt(wCol),
                        height = c.getInt(hCol),
                        mimeType = c.getString(mCol),
                        displayName = c.getString(nCol),
                    )
                }
            }
        }
        result
    }

    /** Emits whenever the media library changes (new photo taken, screenshot, etc). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}

/**
 * Persists the notes for every image so they can be re-opened and edited later.
 * One shared instance per process so the app and the transparent-mode overlay stay in sync.
 */
class NoteStore private constructor(context: Context) {
    companion object {
        @Volatile private var instance: NoteStore? = null
        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) { instance ?: NoteStore(context.applicationContext).also { instance = it } }
    }

    /** Bumped on every save; lets the UI refresh badges when the overlay adds notes. */
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private val file = File(context.filesDir, "notes.json")
    private val data = LinkedHashMap<String, List<TextNote>>()

    init {
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                root.keys().forEach { key ->
                    data[key] = TextNote.listFromJson(root.getJSONArray(key))
                }
            }
        }
    }

    @Synchronized
    fun notesFor(uri: Uri): List<TextNote> = data[uri.toString()].orEmpty()

    @Synchronized
    fun annotatedUris(): Set<String> = data.filterValues { it.isNotEmpty() }.keys.toSet()

    suspend fun save(uri: Uri, notes: List<TextNote>) = withContext(Dispatchers.IO) {
        val snapshot = synchronized(this@NoteStore) {
            if (notes.isEmpty()) data.remove(uri.toString()) else data[uri.toString()] = notes
            JSONObject().apply { data.forEach { (k, v) -> put(k, TextNote.listToJson(v)) } }.toString()
        }
        val tmp = File(file.parentFile, "notes.json.tmp")
        synchronized(file) {
            tmp.writeText(snapshot)
            tmp.renameTo(file)
        }
        changes.value = changes.value + 1
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("imagenote", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings =
        AppSettings.fromJson(prefs.getString("settings", null)?.let { runCatching { JSONObject(it) }.getOrNull() })

    fun saveSettings(s: AppSettings) = prefs.edit().putString("settings", s.toJson().toString()).apply()

    fun loadDefaultStyle(): NoteStyle =
        NoteStyle.fromJson(prefs.getString("style", null)?.let { runCatching { JSONObject(it) }.getOrNull() })

    fun saveDefaultStyle(s: NoteStyle) = prefs.edit().putString("style", s.toJson().toString()).apply()

    fun loadPresets(): List<NoteStyle> = runCatching {
        val a = org.json.JSONArray(prefs.getString("presets", "[]"))
        (0 until a.length()).map { NoteStyle.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePresets(list: List<NoteStyle>) = prefs.edit()
        .putString("presets", org.json.JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        .apply()
}
