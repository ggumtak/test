package com.ggumtak.hanja

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A kanji drawn stroke by stroke, each stroke in the colour [colorOf] gives its tag, so the radical and the sound
 * part stand out from the rest. The strokes come as [Shapes] (parsed once per kanji and shared by every view of it):
 * AnimCJK's brush outlines in a 1024 x 1024 box, filled like a 楷書 font, or KanjiVG's pen lines in a 109 x 109 box,
 * stroked. [sizePx] is the side of the square it asks for (a parent with a fixed size wins).
 *
 * When [numbered], it can show the stroke order: [play] (or a tap) writes the strokes one by one over a faint copy of
 * the kanji, each numbered where the brush starts it, and the numbers stay; until the first play it shows the kanji
 * as it is. A stroke is written the way a brush moves: the outline is revealed along the brush's centre line (the
 * AnimCJK median) from where it goes in to where it lifts, so 一 grows from the left and 亅 turns at its hook; a pen
 * line is drawn along itself.
 */
class KanjiStrokeView(
    ctx: Context,
    private val shapes: Shapes,
    private val sizePx: Int,
    private val colors: Colors,
    private val numbered: Boolean = false,
    colorOf: (Char) -> Int,
) : View(ctx) {
    /** The faint copy behind the strokes still to come, and the number labels. */
    class Colors(val ghost: Int, val labelFill: Int, val labelEdge: Int, val labelText: Int)

    /** One kanji's strokes, parsed: what the views of it share. */
    class Shapes(parts: Parts) {
        val filled = parts.filled
        val box = if (filled) 1024f else 109f
        val tags: List<Char> = parts.strokes.map { it.tag }
        private val segs = parts.strokes.map { SvgPath.parse(it.path) }
        /** The outlines (filled) or the pen lines. */
        val paths: List<Path> = segs.map(::toPath)
        /**
         * The line each stroke is written along with its length: the brush's centre line (stretched a little at both
         * ends so the pen covers where the brush goes in and lifts), or for pen lines the line itself; null when an
         * outline has no centre line (it then fades in).
         */
        val guides: List<PathMeasure?> = if (!filled) paths.map { PathMeasure(it, false) }
            else if (parts.medians.size == paths.size) parts.medians.map { PathMeasure(medianPath(it, box * EXTEND), false) }
            else paths.map { null }
        val labelRadius = box * 0.045f
        /** Where each number goes: the stroke's start, moved aside when an earlier number already sits there. */
        val numberAt: List<Pair<Float, Float>> = StrokeOrder.placeLabels(
            if (parts.starts.size == paths.size) parts.starts
            else segs.map { s -> (s.firstOrNull() as? SvgPath.Seg.Move)?.let { it.x to it.y } ?: (box / 2 to box / 2) },
            labelRadius, box)
        val size: Int get() = paths.size
    }

    private val strokeColors = shapes.tags.map(colorOf)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (shapes.filled) {
            style = Paint.Style.FILL
        } else {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            // KanjiVG draws with 3 units; a little heavier reads like the font next to it.
            strokeWidth = 4.4f
        }
    }
    /**
     * The brush moving along the centre line: wide enough to cover the stroke. Only the shape of its trail is used,
     * cut to the stroke's outline, so a stroke being written is never wider than the stroke itself.
     */
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = shapes.box * PEN
    }
    private val part = Path()
    private val trail = Path()
    private val written = Path()
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = shapes.labelRadius * 1.25f
        isFakeBoldText = true
        color = colors.labelText
    }

    /** How many strokes are written: whole strokes plus the fraction of the one being written. */
    private var progress = shapes.size.toFloat()
    /** The stroke order has been played: strokes not yet written are faint and the written ones numbered. */
    private var ordering = false
    private var animator: ValueAnimator? = null
    /** Each stroke's writing time and when it starts, ms: longer strokes take longer, with a pause between. */
    private val writeMs: FloatArray
    private val startMs: FloatArray
    private val totalMs: Float

    init {
        if (numbered && shapes.size > 0) setOnClickListener { play() }
        val n = shapes.size
        writeMs = FloatArray(n) { i ->
            val len = shapes.guides[i]?.length ?: (shapes.box * 0.4f)
            WRITE_BASE_MS + WRITE_MS_PER_BOX * (len / shapes.box)
        }
        var sum = writeMs.sum() + PAUSE_MS * n
        // A kanji of many strokes is written faster rather than for longer than MAX_MS.
        val scale = if (sum > MAX_MS) MAX_MS / sum else 1f
        for (i in 0 until n) writeMs[i] *= scale
        startMs = FloatArray(n)
        var t = 0f
        for (i in 0 until n) {
            startMs[i] = t
            t += writeMs[i] + PAUSE_MS * scale
        }
        sum = t
        totalMs = sum
    }

    /** Writes the strokes from the first. */
    fun play() {
        animator?.cancel()
        ordering = true
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, totalMs).apply {
            duration = totalMs.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = progressAt(it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    /** The stroke being written at [t] ms and how much of it, as "strokes done + fraction". */
    private fun progressAt(t: Float): Float {
        val n = shapes.size
        if (t >= totalMs) return n.toFloat()
        var i = 0
        while (i + 1 < n && startMs[i + 1] <= t) i++
        return i + ((t - startMs[i]) / writeMs[i]).coerceIn(0f, 1f)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        ordering = false
        progress = shapes.size.toFloat()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(sizePx, widthMeasureSpec), resolveSize(sizePx, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val scale = side / shapes.box
        canvas.save()
        canvas.translate(paddingLeft + (width - paddingLeft - paddingRight - side) / 2f,
            paddingTop + (height - paddingTop - paddingBottom - side) / 2f)
        canvas.scale(scale, scale)
        for (i in 0 until shapes.size) {
            val path = shapes.paths[i]
            val color = strokeColors[i]
            val shown = if (ordering) (progress - i).coerceIn(0f, 1f) else 1f
            if (shown < 1f) {
                // The faint copy where the stroke will be written.
                paint.color = colors.ghost
                canvas.drawPath(path, paint)
            }
            if (shown >= 1f) {
                paint.color = color
                canvas.drawPath(path, paint)
            } else if (shown > 0f) {
                drawPart(canvas, i, color, shown)
            }
        }
        if (ordering) {
            val r = shapes.labelRadius
            for (i in 0 until shapes.size) {
                if (progress <= i) break
                val (x, y) = shapes.numberAt[i]
                circle.style = Paint.Style.FILL
                circle.color = colors.labelFill
                canvas.drawCircle(x, y, r, circle)
                circle.style = Paint.Style.STROKE
                circle.strokeWidth = r * 0.12f
                circle.color = colors.labelEdge
                canvas.drawCircle(x, y, r, circle)
                canvas.drawText((i + 1).toString(), x, y + label.textSize * 0.36f, label)
            }
        }
        canvas.restore()
    }

    /** Stroke [i] written as far as [shown] (0..1). */
    private fun drawPart(canvas: Canvas, i: Int, color: Int, shown: Float) {
        val guide = shapes.guides[i]
        if (guide == null) {
            paint.color = color
            paint.alpha = (255 * shown).toInt()
            canvas.drawPath(shapes.paths[i], paint)
            paint.alpha = 255
            return
        }
        part.reset()
        guide.getSegment(0f, guide.length * shown, part, true)
        if (!shapes.filled) {
            paint.color = color
            canvas.drawPath(part, paint)
            return
        }
        // The part of the stroke's outline the brush's trail has covered, filled in the stroke's colour. Computed as
        // shapes, not by blending a wide pen line with the outline: Android blends only where the outline is drawn,
        // which left the pen's full width showing around the stroke while it was written.
        trail.reset()
        @Suppress("DEPRECATION")
        pen.getFillPath(part, trail)
        paint.color = color
        if (written.op(shapes.paths[i], trail, Path.Op.INTERSECT)) {
            canvas.drawPath(written, paint)
        } else {
            paint.alpha = (255 * shown).toInt()
            canvas.drawPath(shapes.paths[i], paint)
            paint.alpha = 255
        }
    }

    companion object {
        /** Time to write a stroke: a base plus this much for a stroke as long as the box is wide. */
        private const val WRITE_BASE_MS = 150f
        private const val WRITE_MS_PER_BOX = 520f
        private const val PAUSE_MS = 110f
        private const val MAX_MS = 10000f
        /** The pen's width and how far the centre line is stretched at each end, as parts of the box. */
        private const val PEN = 0.2f
        private const val EXTEND = 0.06f

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

        /** The centre line through [xy] (x, y, x, y, …), its ends pushed out by [extend] along the first and last legs. */
        private fun medianPath(xy: FloatArray, extend: Float): Path {
            val p = Path()
            val pts = StrokeOrder.extendLine(xy, extend)
            p.moveTo(pts[0], pts[1])
            var i = 2
            while (i + 1 < pts.size) {
                p.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            // A dot (one point) still needs a length for the pen to move along.
            if (pts.size == 2) p.lineTo(pts[0] + 0.5f, pts[1] + 0.5f)
            return p
        }
    }
}
