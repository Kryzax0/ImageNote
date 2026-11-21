package com.imagenote.app.data

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.imagenote.app.R

data class FontOption(
    val id: String,
    val label: String,
    val resId: Int? = null,
    val systemFamily: String? = null,
    val handwriting: Boolean = true,
)

object Fonts {
    val all = listOf(
        FontOption("caveat", "Caveat", R.font.caveat),
        FontOption("dancing_script", "Dancing Script", R.font.dancing_script),
        FontOption("satisfy", "Satisfy", R.font.satisfy),
        FontOption("pacifico", "Pacifico", R.font.pacifico),
        FontOption("great_vibes", "Great Vibes", R.font.great_vibes),
        FontOption("sacramento", "Sacramento", R.font.sacramento),
        FontOption("homemade_apple", "Homemade Apple", R.font.homemade_apple),
        FontOption("kalam", "Kalam", R.font.kalam),
        FontOption("indie_flower", "Indie Flower", R.font.indie_flower),
        FontOption("patrick_hand", "Patrick Hand", R.font.patrick_hand),
        FontOption("architects_daughter", "Architects Daughter", R.font.architects_daughter),
        FontOption("permanent_marker", "Permanent Marker", R.font.permanent_marker),
        FontOption("sans", "Sans", systemFamily = "sans-serif", handwriting = false),
        FontOption("sans_condensed", "Condensed", systemFamily = "sans-serif-condensed", handwriting = false),
        FontOption("serif", "Serif", systemFamily = "serif", handwriting = false),
        FontOption("mono", "Mono", systemFamily = "monospace", handwriting = false),
    )

    private val cache = HashMap<String, Typeface>()

    fun option(id: String): FontOption = all.firstOrNull { it.id == id } ?: all.first()

    fun typeface(context: Context, id: String): Typeface = synchronized(cache) {
        cache.getOrPut(id) {
            val opt = option(id)
            when {
                opt.resId != null -> runCatching { ResourcesCompat.getFont(context, opt.resId) }.getOrNull()
                opt.systemFamily != null -> Typeface.create(opt.systemFamily, Typeface.NORMAL)
                else -> null
            } ?: Typeface.DEFAULT
        }
    }
}
