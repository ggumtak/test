package com.ggumtak.hanja

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * A kanji drawn stroke by stroke, each stroke in the colour [colorOf] gives its tag, so the radical and the sound
 * part stand out from the rest. When [filled], the strokes are AnimCJK's brush outlines in a 1024 x 1024 box,
 * filled like a 楷書 font; otherwise KanjiVG's pen lines in a 109 x 109 box, stroked. [sizePx] is the side of the
 * square it takes.
 */
class KanjiStrokeView(
    ctx: Context,
    strokes: List<Parts.Stroke>,
    private val filled: Boolean,
    private val sizePx: Int,
    colorOf: (Char) -> Int,
) : View(ctx) {
    private val box = if (filled) 1024f else 109f
    private val paths: List<Pair<Path, Int>> = strokes.map { toPath(it.path) to colorOf(it.tag) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (filled) {
            style = Paint.Style.FILL
        } else {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            // KanjiVG draws with 3 units; a little heavier reads like the font next to it.
            strokeWidth = 4.4f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(sizePx, widthMeasureSpec), resolveSize(sizePx, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val scale = side / box
        canvas.save()
        canvas.translate(paddingLeft + (width - paddingLeft - paddingRight - side) / 2f,
            paddingTop + (height - paddingTop - paddingBottom - side) / 2f)
        canvas.scale(scale, scale)
        for ((path, color) in paths) {
            paint.color = color
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    private companion object {
        fun toPath(d: String): Path {
            val p = Path()
            for (s in SvgPath.parse(d)) when (s) {
                is SvgPath.Seg.Move -> p.moveTo(s.x, s.y)
                is SvgPath.Seg.Line -> p.lineTo(s.x, s.y)
                is SvgPath.Seg.Cubic -> p.cubicTo(s.x1, s.y1, s.x2, s.y2, s.x, s.y)
                is SvgPath.Seg.Quad -> p.quadTo(s.x1, s.y1, s.x, s.y)
                SvgPath.Seg.Close -> p.close()
            }
            return p
        }
    }
}
