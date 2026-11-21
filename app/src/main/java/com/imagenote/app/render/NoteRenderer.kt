package com.imagenote.app.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.imagenote.app.data.BackgroundStyle
import com.imagenote.app.data.Fonts
import com.imagenote.app.data.TextAlign
import com.imagenote.app.data.TextNote
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws notes onto an android.graphics.Canvas. All sizes scale with the image width so the
 * on-screen preview and the saved full-resolution image look identical.
 */
object NoteRenderer {

    class Measured(
        val layout: StaticLayout,
        val paint: TextPaint,
        val fontPx: Float,
        val strokePx: Float,
        val pad: Float,
        val width: Float,
        val height: Float,
    )

    fun fontPx(note: TextNote, imageW: Float) = note.style.size / 1000f * imageW * note.scale

    fun measure(context: Context, note: TextNote, imageW: Float): Measured {
        val style = note.style
        val fontPx = max(1f, fontPx(note, imageW))
        val strokePx = fontPx * style.outlineWidth
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Fonts.typeface(context, style.fontId)
            textSize = fontPx
            textSkewX = -style.slant
            isFakeBoldText = style.bold
            letterSpacing = style.letterSpacing
            color = style.textColor
        }
        val text = displayText(note)
        val maxW = max(fontPx, style.maxWidth * imageW * max(1f, note.scale))
        val desired = text.split('\n').maxOf { ceil(Layout.getDesiredWidth(it, paint)) }
        val layoutW = min(desired + strokePx + 2f, maxW).toInt().coerceAtLeast(1)
        val align = when (style.align) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutW)
            .setAlignment(align)
            .setLineSpacing(0f, style.lineSpacing)
            .setIncludePad(true)
            .build()
        // Padding covers the outline, italic overhang and an optional background box.
        val pad = strokePx + fontPx * (0.12f + style.slant * 0.4f)
        return Measured(
            layout, paint, fontPx, strokePx, pad,
            width = layout.width + pad * 2,
            height = layout.height + pad * 2,
        )
    }

    fun displayText(note: TextNote) =
        (if (note.style.allCaps) note.text.uppercase() else note.text).ifEmpty { " " }

    /**
     * @param imageW / imageH size of the image area on [canvas] in pixels.
     */
    fun draw(
        canvas: Canvas,
        context: Context,
        note: TextNote,
        imageW: Float,
        imageH: Float,
        selected: Boolean = false,
        alpha: Float = 1f,
    ) {
        val m = measure(context, note, imageW)
        val style = note.style
        val a = (alpha * style.opacity.coerceIn(0.05f, 1f) * 255).toInt().coerceIn(0, 255)
        val shadowRadius = m.fontPx * 0.18f

        canvas.save()
        canvas.translate(note.x * imageW, note.y * imageH)
        canvas.rotate(note.rotation)
        canvas.translate(-m.width / 2f, -m.height / 2f)

        // Background box
        when (style.background) {
            BackgroundStyle.TRANSLUCENT, BackgroundStyle.SOLID -> {
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.backgroundColor
                    this.alpha = if (style.background == BackgroundStyle.SOLID) a else (a * 0.45f).toInt()
                }
                val r = m.fontPx * 0.35f
                canvas.drawRoundRect(RectF(0f, 0f, m.width, m.height), r, r, bg)
            }
            else -> Unit
        }

        canvas.translate(m.pad, m.pad)
        val paint = m.paint
        val layout = m.layout

        // Highlighter: a marker band behind each line.
        if (style.background == BackgroundStyle.HIGHLIGHT) {
            val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                this.alpha = (a * 0.85f).toInt()
            }
            val ext = m.fontPx * 0.18f
            for (i in 0 until layout.lineCount) {
                val top = layout.getLineTop(i) + (layout.getLineBottom(i) - layout.getLineTop(i)) * 0.18f
                val bottom = layout.getLineBottom(i) - (layout.getLineBottom(i) - layout.getLineTop(i)) * 0.08f
                val r = RectF(layout.getLineLeft(i) - ext, top, layout.getLineRight(i) + ext, bottom)
                canvas.drawRoundRect(r, m.fontPx * 0.12f, m.fontPx * 0.12f, hl)
            }
        }

        // Neon glow: blurred copies of the text in the glow colour.
        if (style.background == BackgroundStyle.GLOW) {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = m.fontPx * 0.06f
            paint.color = style.backgroundColor
            paint.alpha = a
            for (radius in floatArrayOf(m.fontPx * 0.45f, m.fontPx * 0.2f)) {
                paint.setShadowLayer(radius, 0f, 0f, withAlpha(style.backgroundColor, a))
                layout.draw(canvas)
            }
            paint.clearShadowLayer()
        }

        // Outline pass
        if (m.strokePx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = m.strokePx * 2f // half of the stroke is hidden under the fill
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeMiter = 10f
            paint.color = style.outlineColor
            paint.alpha = a * android.graphics.Color.alpha(style.outlineColor) / 255
            if (style.background == BackgroundStyle.SHADOW) {
                paint.setShadowLayer(shadowRadius, m.fontPx * 0.04f, m.fontPx * 0.06f, withAlpha(0xFF000000.toInt(), a * 2 / 3))
            }
            layout.draw(canvas)
            paint.clearShadowLayer()
        }

        // Fill pass
        paint.style = Paint.Style.FILL
        paint.color = style.textColor
        paint.alpha = a * android.graphics.Color.alpha(style.textColor) / 255
        if (style.background == BackgroundStyle.SHADOW && m.strokePx <= 0f) {
            paint.setShadowLayer(shadowRadius, m.fontPx * 0.04f, m.fontPx * 0.06f, withAlpha(0xFF000000.toInt(), a * 2 / 3))
        }
        layout.draw(canvas)
        paint.clearShadowLayer()

        canvas.translate(-m.pad, -m.pad)

        if (selected) {
            val density = context.resources.displayMetrics.density
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(9f * density, 6f * density), 0f)
            }
            val rect = RectF(0f, 0f, m.width, m.height)
            val r = 12f * density
            dash.color = 0x99000000.toInt(); dash.strokeWidth = 4.5f * density
            canvas.drawRoundRect(rect, r, r, dash)
            dash.color = if (note.locked) 0xFFB0B0B8.toInt() else SELECTION_ORANGE
            dash.strokeWidth = 2.2f * density
            canvas.drawRoundRect(rect, r, r, dash)
        }
        canvas.restore()
    }

    const val SELECTION_ORANGE = 0xFFFF7A1A.toInt()

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** Size and placement of a note in image-area pixels, for handles and toolbars. */
    class Frame(val cx: Float, val cy: Float, val width: Float, val height: Float, val rotation: Float) {
        /** Corner of the (rotated) frame; sx/sy are -1 or 1. */
        fun corner(sx: Int, sy: Int): Pair<Float, Float> {
            val rad = Math.toRadians(rotation.toDouble())
            val dx = sx * width / 2f
            val dy = sy * height / 2f
            return (cx + dx * cos(rad) - dy * sin(rad)).toFloat() to (cy + dx * sin(rad) + dy * cos(rad)).toFloat()
        }

        /** Axis-aligned bounds of the rotated frame. */
        fun bounds(): RectF {
            val pts = listOf(corner(-1, -1), corner(1, -1), corner(1, 1), corner(-1, 1))
            return RectF(pts.minOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.first }, pts.maxOf { it.second })
        }
    }

    fun frame(context: Context, note: TextNote, imageW: Float, imageH: Float): Frame {
        val m = measure(context, note, imageW)
        return Frame(note.x * imageW, note.y * imageH, m.width, m.height, note.rotation)
    }

    /** Returns the top-most note under the point (px, py) in image-area coordinates. */
    fun hitTest(
        context: Context,
        notes: List<TextNote>,
        imageW: Float,
        imageH: Float,
        px: Float,
        py: Float,
        slopPx: Float = 0f,
    ): TextNote? {
        for (note in notes.asReversed()) {
            val m = measure(context, note, imageW)
            val dx = px - note.x * imageW
            val dy = py - note.y * imageH
            val rad = Math.toRadians(-note.rotation.toDouble())
            val lx = (dx * cos(rad) - dy * sin(rad)).toFloat()
            val ly = (dx * sin(rad) + dy * cos(rad)).toFloat()
            if (lx in (-m.width / 2 - slopPx)..(m.width / 2 + slopPx) &&
                ly in (-m.height / 2 - slopPx)..(m.height / 2 + slopPx)
            ) return note
        }
        return null
    }

    /** Height of a note as a fraction of image height; used to stack new notes. */
    fun heightFraction(context: Context, note: TextNote, imageW: Float, imageH: Float): Float =
        measure(context, note, imageW).height / imageH
}
