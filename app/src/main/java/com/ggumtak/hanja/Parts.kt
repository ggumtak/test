package com.ggumtak.hanja

import java.text.Normalizer
import kotlin.math.hypot

/**
 * One line of assets/parts.tsv (tools/build_parts_data.py): the Kangxi [radical] number as Japanese dictionaries
 * file the character (KANJIDIC2), the radical's shape [form] and [position] in this character (氵, left), the
 * top-level [parts] and the [strokes], each tagged as the radical, the sound part or other, and [family]: other
 * 상용한자 with the same sound part and an on reading in common (晴: 青清静情精). The strokes are AnimCJK's brush
 * outlines to fill in a 1024 box when [filled] (all 상용한자), else KanjiVG's pen lines in a 109 box.
 */
data class Parts(
    val radical: Int,
    val form: String,
    val position: String,
    val parts: List<Part>,
    val strokes: List<Stroke>,
    val family: String,
    val filled: Boolean = false,
    /** Where the brush starts each stroke (outlines only; same box), for numbering the strokes. */
    val starts: List<Pair<Float, Float>> = emptyList(),
) {
    /** A top-level part: its shape, the full form it stands for (氵 → 水) and whether it is the radical / sound. */
    data class Part(val element: String, val original: String, val radical: Boolean, val sound: Boolean)

    /** One stroke: [tag] is [RADICAL], [SOUND] or [OTHER]; [path] is SVG path data in a 109 x 109 box. */
    data class Stroke(val tag: Char, val path: String)

    companion object {
        const val RADICAL = 'r'
        const val SOUND = 'p'
        const val OTHER = '-'

        fun parse(line: String): Parts? {
            val f = line.split('\t')
            if (f.size < 2 || f[0].isEmpty()) return null
            val parts = f.getOrElse(4) { "" }.split(' ').filter { it.isNotEmpty() }.mapNotNull { p ->
                val x = p.split('/')
                if (x.size < 3 || x[0].isEmpty()) null
                else Part(x[0], x[1], 'r' in x[2], 'p' in x[2])
            }
            val strokes = f.getOrElse(5) { "" }.split('|').filter { it.length > 1 }.map { Stroke(it[0], it.substring(1)) }
            return Parts(f[1].toIntOrNull() ?: 0, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, parts, strokes,
                f.getOrElse(6) { "" }, f.getOrElse(7) { "" } == "f", parseStarts(f.getOrElse(8) { "" }))
        }

        /** "227,136;143,317" as points; empty when any is malformed. */
        fun parseStarts(s: String): List<Pair<Float, Float>> {
            if (s.isEmpty()) return emptyList()
            val out = ArrayList<Pair<Float, Float>>()
            for (pt in s.split(';')) {
                val xy = pt.split(',')
                val x = xy.getOrNull(0)?.toFloatOrNull() ?: return emptyList()
                val y = xy.getOrNull(1)?.toFloatOrNull() ?: return emptyList()
                out.add(x to y)
            }
            return out
        }

        /** カ for ガ: the sound series ignores voicing (板 バン with 反 ハン), as the build script does. */
        fun unvoiced(kana: String): String =
            Normalizer.normalize(kana, Normalizer.Form.NFD).filter { it != '゙' && it != '゚' }
    }
}

/**
 * One line of assets/radicals.tsv: a radical [form] (氵) of Kangxi radical [number], where it sits ([position], empty
 * for anywhere), its Japanese name ([japanese]: さんずい), its Korean name ([korean]: 삼수변) and, when that name
 * does not say it, a short [meaning] (물).
 */
data class Radical(
    val number: Int,
    val form: String,
    val position: String,
    val japanese: String,
    val korean: String,
    val meaning: String,
) {
    companion object {
        fun parseAll(text: String): List<Radical> = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = line.split('\t')
                val n = f[0].toIntOrNull()
                if (n == null || f.size < 5 || f[1].isEmpty()) null
                else Radical(n, f[1], f[2], f[3], f[4], f.getOrElse(5) { "" })
            }.toList()

        /**
         * The name to show for radical [number] drawn as [form] at [position]: that form at that position, else that
         * form anywhere, else the radical's main line (its first), whose form is then shown instead.
         */
        fun find(all: List<Radical>, number: Int, form: String, position: String): Radical? {
            val mine = all.filter { it.number == number }
            if (mine.isEmpty()) return null
            if (form.isNotEmpty()) {
                mine.firstOrNull { it.form == form && it.position == position && position.isNotEmpty() }?.let { return it }
                mine.firstOrNull { it.form == form && it.position.isEmpty() }?.let { return it }
                mine.firstOrNull { it.form == form }?.let { return it }
            }
            return mine.firstOrNull { it.position.isEmpty() } ?: mine.first()
        }
    }
}

/** One line of assets/origin.tsv: how the character came to its shape ("형성: …") and a memory story ("… → 바다"). */
data class Origin(val origin: String, val memo: String) {
    /** "형성" of "형성: 氵(물)가 뜻…", or empty. */
    val type: String get() = origin.substringBefore(':', "").takeIf { it.length <= 8 }.orEmpty()

    companion object {
        fun parse(value: String): Origin? {
            val f = value.split('\t')
            val origin = f[0].trim()
            if (origin.isEmpty()) return null
            return Origin(origin, f.getOrElse(1) { "" }.trim())
        }
    }
}

/**
 * SVG path data (KanjiVG uses M m C c S s; the AnimCJK outlines M m c q z) as absolute segments, so the view can
 * turn them into a Path and the parsing can be tested without Android.
 */
object SvgPath {
    sealed class Seg {
        data class Move(val x: Float, val y: Float) : Seg()
        data class Line(val x: Float, val y: Float) : Seg()
        data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Seg()
        data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : Seg()
        object Close : Seg()
    }

    private val TOKEN = Regex("[MmLlHhVvCcSsQqTtZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    fun parse(d: String): List<Seg> {
        val out = ArrayList<Seg>()
        val tokens = TOKEN.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = ' '
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        // The second control point of the last cubic, for S's reflection; the last quadratic's control point, for T's.
        var cx = 0f
        var cy = 0f
        var lastCubic = false
        var qx = 0f
        var qy = 0f
        var lastQuad = false

        fun hasNumber() = i < tokens.size && !tokens[i][0].isLetter()
        fun num(): Float = tokens[i++].toFloat()

        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) {
                cmd = tokens[i++][0]
            } else if (cmd == ' ') {
                i++
                continue
            }
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    if (!hasNumber()) break
                    x = ox + num(); if (!hasNumber()) break; y = oy + num()
                    startX = x; startY = y
                    out.add(Seg.Move(x, y))
                    lastCubic = false
                    // Further pairs after a move are lines.
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> {
                    if (!hasNumber()) break
                    x = ox + num(); if (!hasNumber()) break; y = oy + num()
                    out.add(Seg.Line(x, y)); lastCubic = false
                }
                'H' -> { if (!hasNumber()) break; x = ox + num(); out.add(Seg.Line(x, y)); lastCubic = false }
                'V' -> { if (!hasNumber()) break; y = oy + num(); out.add(Seg.Line(x, y)); lastCubic = false }
                'C' -> {
                    if (i + 6 > tokens.size || (0 until 6).any { tokens[i + it][0].isLetter() }) break
                    val x1 = ox + num(); val y1 = oy + num()
                    val x2 = ox + num(); val y2 = oy + num()
                    x = ox + num(); y = oy + num()
                    out.add(Seg.Cubic(x1, y1, x2, y2, x, y))
                    cx = x2; cy = y2; lastCubic = true
                }
                'S' -> {
                    if (i + 4 > tokens.size || (0 until 4).any { tokens[i + it][0].isLetter() }) break
                    val x1 = if (lastCubic) 2 * x - cx else x
                    val y1 = if (lastCubic) 2 * y - cy else y
                    val x2 = ox + num(); val y2 = oy + num()
                    x = ox + num(); y = oy + num()
                    out.add(Seg.Cubic(x1, y1, x2, y2, x, y))
                    cx = x2; cy = y2; lastCubic = true
                }
                'Q' -> {
                    if (i + 4 > tokens.size || (0 until 4).any { tokens[i + it][0].isLetter() }) break
                    val x1 = ox + num(); val y1 = oy + num()
                    x = ox + num(); y = oy + num()
                    out.add(Seg.Quad(x1, y1, x, y))
                    qx = x1; qy = y1; lastQuad = true; lastCubic = false
                    continue
                }
                'T' -> {
                    if (i + 2 > tokens.size || (0 until 2).any { tokens[i + it][0].isLetter() }) break
                    val x1 = if (lastQuad) 2 * x - qx else x
                    val y1 = if (lastQuad) 2 * y - qy else y
                    x = ox + num(); y = oy + num()
                    out.add(Seg.Quad(x1, y1, x, y))
                    qx = x1; qy = y1; lastQuad = true; lastCubic = false
                    continue
                }
                'Z' -> {
                    out.add(Seg.Close)
                    x = startX; y = startY; lastCubic = false
                    cmd = ' '
                }
                else -> i++
            }
            // Q and T continue above: any other segment ends a run of quadratics.
            lastQuad = false
        }
        return out
    }
}

/** Where KanjiStrokeView puts the stroke numbers. */
object StrokeOrder {
    /**
     * The label centres: each at its stroke's start, unless it would overlap an earlier label (two strokes starting
     * at the same place, as 宀's second and third), then moved around it to the nearest free spot inside the box.
     */
    fun placeLabels(points: List<Pair<Float, Float>>, r: Float, box: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        val dirs = listOf(1f to 0f, 0f to 1f, -1f to 0f, 0f to -1f, 0.7f to 0.7f, -0.7f to 0.7f, 0.7f to -0.7f,
            -0.7f to -0.7f)
        for ((x, y) in points) {
            fun free(px: Float, py: Float) = out.all { (ox, oy) -> hypot(px - ox, py - oy) >= r * 1.9f }
            var best = x.coerceIn(r, box - r) to y.coerceIn(r, box - r)
            if (!free(best.first, best.second)) {
                search@ for (step in 1..4) for ((dx, dy) in dirs) {
                    val px = (x + dx * r * 2f * step).coerceIn(r, box - r)
                    val py = (y + dy * r * 2f * step).coerceIn(r, box - r)
                    if (free(px, py)) {
                        best = px to py
                        break@search
                    }
                }
            }
            out.add(best)
        }
        return out
    }
}
