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
        val a = (alpha * 255).toInt().coerceIn(0, 255)

        canvas.save()
        canvas.translate(note.x * imageW, note.y * imageH)
        canvas.rotate(note.rotation)
        canvas.translate(-m.width / 2f, -m.height / 2f)

        // Background
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
                paint.setShadowLayer(m.fontPx * 0.18f, m.fontPx * 0.04f, m.fontPx * 0.06f, 0xAA000000.toInt())
            }
            m.layout.draw(canvas)
            paint.clearShadowLayer()
        }

        // Fill pass
        paint.style = Paint.Style.FILL
        paint.color = style.textColor
        paint.alpha = a * android.graphics.Color.alpha(style.textColor) / 255
        if (style.background == BackgroundStyle.SHADOW && m.strokePx <= 0f) {
            paint.setShadowLayer(m.fontPx * 0.18f, m.fontPx * 0.04f, m.fontPx * 0.06f, 0xAA000000.toInt())
        }
        m.layout.draw(canvas)
        paint.clearShadowLayer()

        canvas.translate(-m.pad, -m.pad)

        if (selected) {
            val density = context.resources.displayMetrics.density
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 2f * density
                pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
            }
            val rect = RectF(0f, 0f, m.width, m.height)
            val r = 10f * density
            dash.color = 0xCC000000.toInt(); dash.strokeWidth = 4f * density
            canvas.drawRoundRect(rect, r, r, dash)
            dash.color = 0xFFFFFFFF.toInt(); dash.strokeWidth = 2f * density
            canvas.drawRoundRect(rect, r, r, dash)
        }
        canvas.restore()
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
