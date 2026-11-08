package com.ggumtak.hanja

import java.text.Normalizer
import kotlin.math.hypot

/**
 * One line of assets/parts.tsv (tools/build_parts_data.py): the Kangxi [radical] number as Japanese dictionaries
 * file the character (KANJIDIC2), the radical's shape [form] and [position] in this character (氵, left), the
 * top-level [parts] and the [strokes], each tagged as the radical, the sound part or other, and [family]: other
 * 상용한자 with the same sound part and an on reading in common, the 소리 계열 (晴: 青清静情精). The strokes are AnimCJK's brush
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
    /** The line the brush follows through each stroke (outlines only; same box) as x, y, x, y, …, to write it along. */
    val medians: List<FloatArray> = emptyList(),
) {
    /**
     * A top-level part: its shape, the full form it stands for (氵 → 水; for a sound part put together from pieces,
     * the pieces: 氐 → 氏+一) and its role, as the stroke colours show it: the radical itself, the sound part, or a
     * part that only holds the radical ([radicalInside]: 昼's 旦 holds 日) or the sound part ([soundInside], then
     * [inner] names it when known: 塗's 涂 holds 余).
     */
    data class Part(
        val element: String,
        val original: String,
        val radical: Boolean,
        val sound: Boolean,
        val radicalInside: Boolean = false,
        val soundInside: Boolean = false,
        val inner: String = "",
    )

    /** The radical is this part or lies inside the sound part: the dictionary files the kanji there, the meaning is elsewhere. */
    val radicalInSound: Boolean get() = parts.any { it.sound && (it.radical || it.radicalInside) }

    /** The sound part's element when there is one sound part (晴 → 青), else empty. */
    val soundElement: String get() = parts.filter { it.sound }.singleOrNull()?.element.orEmpty()

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
                else Part(x[0], x[1], 'r' in x[2], 'p' in x[2], 'R' in x[2], 'P' in x[2], x.getOrElse(3) { "" })
            }
            val strokes = f.getOrElse(5) { "" }.split('|').filter { it.length > 1 }.map { Stroke(it[0], it.substring(1)) }
            return Parts(f[1].toIntOrNull() ?: 0, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, parts, strokes,
                f.getOrElse(6) { "" }, f.getOrElse(7) { "" } == "f", parseStarts(f.getOrElse(8) { "" }),
                parseMedians(f.getOrElse(9) { "" }, strokes.size))
        }

        /** "1,2,3,4|5,6" as one array per stroke; empty when malformed or not one per stroke ([count]). */
        fun parseMedians(s: String, count: Int): List<FloatArray> {
            if (s.isEmpty()) return emptyList()
            val out = s.split('|').map { st ->
                val v = st.split(',')
                if (v.size < 2 || v.size % 2 != 0) return emptyList()
                val a = FloatArray(v.size)
                for (i in v.indices) a[i] = v[i].toFloatOrNull() ?: return emptyList()
                a
            }
            return if (out.size == count) out else emptyList()
        }

        /**
         * "13획", or "13획 · 사전에 따라 12획" when the dictionary counts [dict] strokes but the drawing (the 2010
         * 상용한자 shape for 僅 謎 餌 …) writes [drawn]: the number the stroke order counts to comes first.
         */
        fun strokeLabel(dict: Int, drawn: Int): String = when {
            drawn <= 0 -> if (dict > 0) "${dict}획" else ""
            dict <= 0 || dict == drawn -> "${drawn}획"
            else -> "${drawn}획 · 사전에 따라 ${dict}획"
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

/**
 * One line of assets/origin.tsv: how the character came to its shape ("형성: …"), a memory story ("… → 바다") and,
 * for the 상용한자 of two or more parts, the 구성 line written to match it ([kosei], see [Kosei]).
 */
data class Origin(val origin: String, val memo: String, val kosei: String = "") {
    /** "형성" of "형성: 氵(물)가 뜻…", or empty. */
    val type: String get() = origin.substringBefore(':', "").takeIf { it.length <= 8 }.orEmpty()

    /** What the card calls the type, in plain words a learner needs no term for (형성 → 뜻+소리), or empty. */
    val label: String get() = LABELS[type] ?: type

    /** The note after the type ("뜻은 氵(물), 소리는 毎(マイ). …"). */
    val body: String get() = if (type.isEmpty()) origin else origin.substringAfter(':').trim()

    companion object {
        /** The six ways of forming characters and the rest, as the card names them. */
        val LABELS = mapOf(
            "상형" to "그림", "지사" to "기호", "회의" to "뜻+뜻", "형성" to "뜻+소리", "회의 겸 형성" to "뜻+소리",
            "가차" to "빌린 글자", "국자" to "일본에서 만든 글자", "생략형" to "줄인 글자", "불명" to "유래 불확실",
        )

        fun parse(value: String): Origin? {
            val f = value.split('\t')
            val origin = f[0].trim()
            if (origin.isEmpty()) return null
            return Origin(origin, f.getOrElse(1) { "" }.trim(), f.getOrElse(2) { "" }.trim())
        }
    }
}

/**
 * The 구성 line as origin.tsv writes it (tools/origin/kosei/kosei.py): the parts in order, " + " between them, each
 * `SHAPE[ROLE](NOTE)` and marked `^` when it is the radical's part or `~` when it holds the radical:
 * `聿[뜻](붓) + ^曰[소리 シャ](者를 줄인 꼴)`, shown as 聿(붓 · 뜻) + 曰(者를 줄인 꼴 · 소리 シャ). The notes use the 유래
 * line's own words, so the two lines read as one explanation. A leading "!" marks a line written freely (its
 * parts are not the drawing's).
 */
object Kosei {
    data class Unit(
        val shape: String,
        /** 뜻, 소리, 뜻·소리, 모양, 기호 or empty. */
        val role: String,
        /** The sound part's on reading in katakana, or empty. */
        val reading: String,
        val note: String,
        val radical: Boolean,
        val holdsRadical: Boolean,
    ) {
        val sound: Boolean get() = "소리" in role
        /** The pieces of the shape (牛+巾 → 牛, 巾). */
        val pieces: List<String> get() = shape.split('+').filter { it.isNotEmpty() }
    }

    private val UNIT = Regex("""^([\^~]?)([^\[\]()]+?)(?:\[([^\]]+)])?(?:\(([^()]*)\))?$""")

    /** The units, or null when the line is empty or malformed (the card then builds the line itself). */
    fun parse(spec: String): List<Unit>? {
        val body = spec.removePrefix("!").trim()
        if (body.isEmpty()) return null
        return body.split(" + ").map { raw ->
            val m = UNIT.find(raw.trim()) ?: return null
            val role = m.groupValues[3].trim().split(' ')
            Unit(m.groupValues[2].trim(), role[0], role.getOrElse(1) { "" }, m.groupValues[4].trim(),
                m.groupValues[1] == "^", m.groupValues[1] == "~")
        }
    }

    /**
     * Whether the radical here only gives the sound, or sits inside the sound part (修's 亻 in 攸): then it just files
     * the kanji in dictionaries. Not when the radical's own part gives the meaning as well (酒's 酉 · 뜻·소리).
     */
    fun radicalOnlySound(units: List<Unit>): Boolean =
        units.any { (it.radical || it.holdsRadical) && it.sound && !(it.radical && "뜻" in it.role) }

    private val CHANGED = Regex("(변한|줄인|바뀐|바꾼|바꿔 쓴) 꼴")

    /**
     * What the 구성 line calls the radical's part when that is not what the radical's name says, so the 부수 row
     * can bridge to it (会: 人 사람인 · 여기서는 '뚜껑'; 服: 月 달월변 · 여기서는 '배 舟가 변한 꼴'), or null. Only
     * for a part drawn as a shape or a sign, or one the note says changed from another character: a part that
     * gives the meaning in its own words (審's 宀 '덮다' under 갓머리(집)) needs no bridge.
     */
    fun radicalHere(units: List<Unit>, korean: String, meaning: String): String? {
        val u = units.firstOrNull { it.radical } ?: return null
        if (u.note.isEmpty()) return null
        if (u.role !in setOf("", "모양", "기호") && !CHANGED.containsMatchIn(u.note)) return null
        val names = words(korean) + words(meaning)
        val related = words(u.note).any { w ->
            names.any { n -> w in n || n in w || (w.length >= 2 && n.length >= 2 && w.take(2) == n.take(2)) }
        }
        return if (related) null else u.note
    }

    /** The Hangul words of a note or a name ("뚜껑", "사람인"), without kanji and signs. */
    private fun words(s: String): List<String> =
        s.split(' ', '·', ',', '(', ')').filter { w -> w.isNotEmpty() && w.all { it in '가'..'힣' } }
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

/** Where KanjiStrokeView puts the stroke numbers, and the line it writes each stroke along. */
object StrokeOrder {
    /**
     * The centre line [xy] (x, y, x, y, …) with its first point moved back by [by] along the first leg and its last
     * moved on by [by] along the last, so a round pen on it covers where the brush goes in and lifts; a single
     * point, or legs of no length, stay as they are.
     */
    fun extendLine(xy: FloatArray, by: Float): FloatArray {
        val out = xy.copyOf()
        val n = xy.size / 2
        if (n < 2) return out
        fun push(at: Int, from: Int) {
            val dx = xy[2 * at] - xy[2 * from]
            val dy = xy[2 * at + 1] - xy[2 * from + 1]
            val d = hypot(dx, dy)
            if (d <= 0f) return
            out[2 * at] = xy[2 * at] + dx / d * by
            out[2 * at + 1] = xy[2 * at + 1] + dy / d * by
        }
        push(0, 1)
        push(n - 1, n - 2)
        return out
    }

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
