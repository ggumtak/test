package com.ggumtak.hanja

/**
 * Which reading each kanji of a word takes (電気 でんき → でん, き), so the card can say "in this word" next to the
 * character's general readings. Sound changes are allowed: voicing in the middle of a word (人々 ひとびと), a
 * small っ for the last kana (学校 がっこう), 々 repeating the kanji before it. A word read as a whole
 * (部屋 へや, 今日 きょう) does not split: [split] returns null.
 */
object Readings {
    /** KANJIDIC's readings of a kanji as hiragana candidates: on, kun stems, kun with okurigana. */
    fun candidates(k: Kanji): List<String> {
        val out = LinkedHashSet<String>()
        for (on in k.on.split('・')) on.trim('-', ' ').takeIf { it.isNotEmpty() }?.let { out.add(hira(it)) }
        for (kun in k.kun.split('・')) {
            val s = kun.trim('-', ' ')
            if (s.isEmpty()) continue
            out.add(s.substringBefore('.'))
            out.add(s.replace(".", ""))
        }
        return out.toList()
    }

    fun hira(s: String): String = String(CharArray(s.length) { i -> s[i].let { if (it in 'ァ'..'ヶ') it - 0x60 else it } })

    private const val VOICED_FROM = "かきくけこさしすせそたちつてとはひふへほ"
    private const val VOICED_TO = "がぎぐげござじずぜぞだぢづでどばびぶべぼ"
    private const val HALF_TO = "ぱぴぷぺぽ"

    /** [r] and its sound-changed forms: voiced first kana (not at the word's start), っ for a final つくちき. */
    private fun variants(r: String, wordStart: Boolean): List<String> {
        val out = ArrayList<String>(4)
        out.add(r)
        if (!wordStart && r.isNotEmpty()) {
            val i = VOICED_FROM.indexOf(r[0])
            if (i >= 0) {
                out.add(VOICED_TO[i] + r.substring(1))
                if (i >= 15) out.add(HALF_TO[i - 15] + r.substring(1))
            }
        }
        for (v in out.toList()) if (v.length >= 2 && v.last() in "つくちき") out.add(v.dropLast(1) + "っ")
        return out
    }

    /**
     * The reading of each character of [word] (kana map to themselves) when [reading] can be split along
     * [readingsOf] (a kanji's candidates, null when unknown), else null.
     */
    fun split(word: String, reading: String, readingsOf: (String) -> List<String>?): List<String>? {
        val chars = ArrayList<String>()
        var i = 0
        while (i < word.length) {
            val n = Character.charCount(word.codePointAt(i))
            chars.add(word.substring(i, i + n))
            i += n
        }
        if (chars.size > 12) return null
        val r = hira(reading)
        val out = arrayOfNulls<String>(chars.size)
        fun go(ci: Int, ri: Int): Boolean {
            if (ci == chars.size) return ri == r.length
            val ch = chars[ci]
            val cp = ch.codePointAt(0)
            if (!KanjiText.isKanji(cp) && ch != "々") {
                val h = hira(ch)
                if (!r.startsWith(h, ri)) return false
                out[ci] = h
                return go(ci + 1, ri + h.length)
            }
            val base = if (ch == "々") (if (ci > 0) chars[ci - 1] else return false) else ch
            val cands = readingsOf(base) ?: return false
            val tries = cands.flatMap { variants(it, ci == 0) }.distinct().sortedByDescending { it.length }
            for (t in tries) {
                if (t.isNotEmpty() && r.startsWith(t, ri)) {
                    out[ci] = t
                    if (go(ci + 1, ri + t.length)) return true
                }
            }
            return false
        }
        return if (go(0, 0)) out.map { it!! } else null
    }

    /**
     * Kana that start a particle after a noun (雨が, 今日は) and rarely start okurigana. か and や are left out:
     * they start okurigana often (暖か, 静か, 冷やす).
     */
    private const val PARTICLES = "はがをにのへとでも"

    /**
     * The JMdict forms to try for [text], best first. Two or more characters: [text] itself first (今日 in
     * 今日は is きょう, not the greeting 今日は), then [text] with up to three kana that follow it in [sentence],
     * then its shorter prefixes. A single kanji: with the following kana first (暖 in 暖かく → 暖か); alone only
     * when nothing follows or a particle does (雨が → 雨), since 暖 alone is だん, not the 暖 of 暖かく.
     */
    fun lookupForms(text: String, sentence: String): List<String> {
        val out = ArrayList<String>()
        val at = if (sentence.isEmpty()) -1 else sentence.indexOf(text)
        var follow = ""
        if (at >= 0) {
            var e = at + text.length
            while (e < sentence.length && e - at - text.length < 3 && sentence[e] in '\u3041'..'\u3096') e++
            follow = sentence.substring(at + text.length, e)
        }
        val single = text.codePointCount(0, text.length) == 1
        if (!single) out.add(text)
        for (n in follow.length downTo 1) out.add(text + follow.substring(0, n))
        if (single && (follow.isEmpty() || follow[0] in PARTICLES)) out.add(text)
        var t = text
        while (t.length > 2) {
            t = t.substring(0, t.length - 1)
            if (t.any { KanjiText.isKanji(it.code) }) out.add(t)
        }
        return out.distinct()
    }

    /** The first of [lookupForms] that [readingsOf] knows, with its readings. */
    fun findWord(text: String, sentence: String, readingsOf: (String) -> List<String>): Pair<String, List<String>>? {
        for (form in lookupForms(text, sentence)) {
            val r = readingsOf(form)
            if (r.isNotEmpty()) return form to r
        }
        return null
    }

    private fun chars(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val n = Character.charCount(s.codePointAt(i))
            out.add(s.substring(i, i + n))
            i += n
        }
        return out
    }

    /** [run] read through per-kanji [parts] (kana as they are); null when a kanji has no part or [run] has none. */
    fun fromParts(run: String, parts: Map<String, String>): String? {
        val cs = chars(run)
        if (cs.none { KanjiText.isKanji(it.codePointAt(0)) }) return null
        val sb = StringBuilder()
        for (ch in cs) {
            if (KanjiText.isKanji(ch.codePointAt(0)) || ch == "々") sb.append(parts[ch] ?: return null)
            else sb.append(hira(ch))
        }
        return sb.toString()
    }

    /** The reading of [run] when it starts [form] (暖 of 暖か) and [parts] is form's split, else null. */
    fun prefixReading(run: String, form: String, parts: List<String>): String? {
        if (!form.startsWith(run)) return null
        val n = chars(run).size
        if (n > parts.size) return null
        return parts.take(n).joinToString("")
    }
}
