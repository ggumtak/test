package com.ggumtak.hanja

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A kanji drawn stroke by stroke, each stroke in the colour [colorOf] gives its tag, so the radical and the sound
 * part stand out from the rest. When [filled], the strokes are AnimCJK's brush outlines in a 1024 x 1024 box,
 * filled like a 楷書 font; otherwise KanjiVG's pen lines in a 109 x 109 box, stroked. [sizePx] is the side of the
 * square it takes.
 *
 * When [numbered], it can show the stroke order: [play] (or a tap) writes the strokes one by one over a faint copy of
 * the kanji, each numbered where the brush starts it ([starts], else the start of its path), and the numbers stay;
 * until the first play it shows the kanji as it is.
 */
class KanjiStrokeView(
    ctx: Context,
    strokes: List<Parts.Stroke>,
    private val filled: Boolean,
    starts: List<Pair<Float, Float>>,
    private val sizePx: Int,
    private val colors: Colors,
    private val numbered: Boolean = false,
    colorOf: (Char) -> Int,
) : View(ctx) {
    /** The faint copy behind the strokes still to come, and the number labels. */
    class Colors(val ghost: Int, val labelFill: Int, val labelEdge: Int, val labelText: Int)

    private val box = if (filled) 1024f else 109f
    private val segs = strokes.map { SvgPath.parse(it.path) }
    private val paths: List<Pair<Path, Int>> = strokes.indices.map { toPath(segs[it]) to colorOf(strokes[it].tag) }
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
    private val labelRadius = box * 0.045f
    /** Where each number goes: the stroke's start, moved aside when an earlier number already sits there. */
    private val numberAt: List<Pair<Float, Float>> = StrokeOrder.placeLabels(
        if (starts.size == strokes.size) starts
        else segs.map { s -> (s.firstOrNull() as? SvgPath.Seg.Move)?.let { it.x to it.y } ?: (box / 2 to box / 2) },
        labelRadius, box)
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = labelRadius * 1.25f
        isFakeBoldText = true
        color = colors.labelText
    }

    /** How many strokes are written: whole strokes plus the fraction of the one appearing. */
    private var progress = paths.size.toFloat()
    /** The stroke order has been played: strokes not yet written are faint and the written ones numbered. */
    private var ordering = false
    private var animator: ValueAnimator? = null

    init {
        if (numbered && paths.isNotEmpty()) setOnClickListener { play() }
    }

    /** Writes the strokes from the first, one every [STROKE_MS]. */
    fun play() {
        animator?.cancel()
        ordering = true
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, paths.size.toFloat()).apply {
            duration = (paths.size * STROKE_MS).coerceAtMost(MAX_MS)
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        ordering = false
        progress = paths.size.toFloat()
        super.onDetachedFromWindow()
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
        for ((i, pc) in paths.withIndex()) {
            val (path, color) = pc
            val shown = if (ordering) (progress - i).coerceIn(0f, 1f) else 1f
            if (shown < 1f) {
                // The faint copy where the stroke will be written.
                paint.color = colors.ghost
                canvas.drawPath(path, paint)
            }
            if (shown > 0f) {
                paint.color = color
                paint.alpha = (255 * shown).toInt()
                canvas.drawPath(path, paint)
                paint.alpha = 255
            }
        }
        if (ordering) {
            for (i in paths.indices) {
                if (progress <= i) break
                val (x, y) = numberAt[i]
                circle.style = Paint.Style.FILL
                circle.color = colors.labelFill
                canvas.drawCircle(x, y, labelRadius, circle)
                circle.style = Paint.Style.STROKE
                circle.strokeWidth = labelRadius * 0.12f
                circle.color = colors.labelEdge
                canvas.drawCircle(x, y, labelRadius, circle)
                canvas.drawText((i + 1).toString(), x, y + label.textSize * 0.36f, label)
            }
        }
        canvas.restore()
    }

    companion object {
        private const val STROKE_MS = 420L
        private const val MAX_MS = 10000L

        private fun toPath(segs: List<SvgPath.Seg>): Path {
            val p = Path()
            for (s in segs) when (s) {
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
