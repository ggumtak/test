package com.ggumtak.hanja

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * A kanji drawn from its KanjiVG strokes (109 x 109 box), each stroke in the colour [colorOf] gives its tag: the
 * radical and the sound part stand out from the rest. [sizePx] is the side of the square it takes.
 */
class KanjiStrokeView(
    ctx: Context,
    strokes: List<Parts.Stroke>,
    private val sizePx: Int,
    colorOf: (Char) -> Int,
) : View(ctx) {
    private val paths: List<Pair<Path, Int>> = strokes.map { toPath(it.path) to colorOf(it.tag) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // KanjiVG draws with 3 units; a little heavier reads like the font next to it.
        strokeWidth = 4.4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(sizePx, widthMeasureSpec), resolveSize(sizePx, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val scale = side / BOX
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
        const val BOX = 109f

        fun toPath(d: String): Path {
            val p = Path()
            for (s in SvgPath.parse(d)) when (s) {
                is SvgPath.Seg.Move -> p.moveTo(s.x, s.y)
                is SvgPath.Seg.Line -> p.lineTo(s.x, s.y)
                is SvgPath.Seg.Cubic -> p.cubicTo(s.x1, s.y1, s.x2, s.y2, s.x, s.y)
                SvgPath.Seg.Close -> p.close()
            }
            return p
        }
    }
}
