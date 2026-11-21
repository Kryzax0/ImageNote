package com.imagenote.app.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** An image from the device gallery. */
data class GalleryImage(
    val uri: Uri,
    val id: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val mimeType: String?,
    val displayName: String?,
)

enum class TextAlign { LEFT, CENTER, RIGHT }

/** How the area behind the text is painted. Default is fully transparent. */
enum class BackgroundStyle(val label: String) {
    NONE("None"),
    SHADOW("Soft shadow"),
    TRANSLUCENT("Dim box"),
    SOLID("Solid box"),
}

/**
 * Everything about how a note looks. Sizes are relative to the image width so a note looks the
 * same on the preview and on the full-resolution export.
 */
data class NoteStyle(
    val fontId: String = "caveat",
    /** Font size as a fraction of the image width / 1000 (e.g. 60 = 6% of image width). */
    val size: Float = 60f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    /** Outline thickness as a fraction of font size (0 – 0.3). */
    val outlineWidth: Float = 0.12f,
    /** Italic slant amount, 0 = upright, 0.5 = very slanted. */
    val slant: Float = 0.18f,
    val bold: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.0f,
    val align: TextAlign = TextAlign.CENTER,
    val background: BackgroundStyle = BackgroundStyle.NONE,
    val backgroundColor: Int = 0xFF000000.toInt(),
    /** Max line width as a fraction of image width before wrapping. */
    val maxWidth: Float = 0.85f,
    val allCaps: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fontId", fontId)
        put("size", size.toDouble())
        put("textColor", textColor)
        put("outlineColor", outlineColor)
        put("outlineWidth", outlineWidth.toDouble())
        put("slant", slant.toDouble())
        put("bold", bold)
        put("letterSpacing", letterSpacing.toDouble())
        put("lineSpacing", lineSpacing.toDouble())
        put("align", align.name)
        put("background", background.name)
        put("backgroundColor", backgroundColor)
        put("maxWidth", maxWidth.toDouble())
        put("allCaps", allCaps)
    }

    companion object {
        fun fromJson(o: JSONObject?): NoteStyle {
            if (o == null) return NoteStyle()
            val d = NoteStyle()
            return NoteStyle(
                fontId = o.optString("fontId", d.fontId),
                size = o.optDouble("size", d.size.toDouble()).toFloat(),
                textColor = o.optInt("textColor", d.textColor),
                outlineColor = o.optInt("outlineColor", d.outlineColor),
                outlineWidth = o.optDouble("outlineWidth", d.outlineWidth.toDouble()).toFloat(),
                slant = o.optDouble("slant", d.slant.toDouble()).toFloat(),
                bold = o.optBoolean("bold", d.bold),
                letterSpacing = o.optDouble("letterSpacing", d.letterSpacing.toDouble()).toFloat(),
                lineSpacing = o.optDouble("lineSpacing", d.lineSpacing.toDouble()).toFloat(),
                align = runCatching { TextAlign.valueOf(o.optString("align")) }.getOrDefault(d.align),
                background = runCatching { BackgroundStyle.valueOf(o.optString("background")) }
                    .getOrDefault(d.background),
                backgroundColor = o.optInt("backgroundColor", d.backgroundColor),
                maxWidth = o.optDouble("maxWidth", d.maxWidth.toDouble()).toFloat(),
                allCaps = o.optBoolean("allCaps", d.allCaps),
            )
        }
    }
}

/** A single spoken note placed on an image. Position is normalized (0..1) to the image. */
data class TextNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.8f,
    /** Extra user scale from pinch gestures, multiplied with style.size. */
    val scale: Float = 1f,
    /** Rotation in degrees. */
    val rotation: Float = 0f,
    val style: NoteStyle = NoteStyle(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("scale", scale.toDouble())
        put("rotation", rotation.toDouble())
        put("style", style.toJson())
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = TextNote(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text"),
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.8).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            style = NoteStyle.fromJson(o.optJSONObject("style")),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )

        fun listToJson(notes: List<TextNote>) = JSONArray().apply { notes.forEach { put(it.toJson()) } }
        fun listFromJson(a: JSONArray): List<TextNote> =
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) }
    }
}

data class AppSettings(
    /** How long the user must be quiet before the note is finished. */
    val silenceMillis: Long = 2000L,
    val preferOnDevice: Boolean = true,
    /** BCP-47 tag, empty = device language. */
    val language: String = "",
    val autoCapitalize: Boolean = true,
    val haptics: Boolean = true,
    val jpegQuality: Int = 95,
    /** Put each new note below the previous one instead of on top of it. */
    val stackNotes: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("silenceMillis", silenceMillis)
        put("preferOnDevice", preferOnDevice)
        put("language", language)
        put("autoCapitalize", autoCapitalize)
        put("haptics", haptics)
        put("jpegQuality", jpegQuality)
        put("stackNotes", stackNotes)
    }

    companion object {
        fun fromJson(o: JSONObject?): AppSettings {
            if (o == null) return AppSettings()
            val d = AppSettings()
            return AppSettings(
                silenceMillis = o.optLong("silenceMillis", d.silenceMillis),
                preferOnDevice = o.optBoolean("preferOnDevice", d.preferOnDevice),
                language = o.optString("language", d.language),
                autoCapitalize = o.optBoolean("autoCapitalize", d.autoCapitalize),
                haptics = o.optBoolean("haptics", d.haptics),
                jpegQuality = o.optInt("jpegQuality", d.jpegQuality),
                stackNotes = o.optBoolean("stackNotes", d.stackNotes),
            )
        }
    }
}
