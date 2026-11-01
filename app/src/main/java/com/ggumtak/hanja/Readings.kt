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
     * The JMdict forms to try for [text], best first: [text] with up to three kana that follow it in [sentence]
     * (longest first, for 暖 in 暖かく → 暖か), then [text] and its shorter prefixes down to two characters. A single kanji
     * followed by kana is not looked up alone (暖 alone is だん, not the 暖 of 暖かく).
     */
    fun lookupForms(text: String, sentence: String): List<String> {
        val out = ArrayList<String>()
        val at = if (sentence.isEmpty()) -1 else sentence.indexOf(text)
        var follow = ""
        if (at >= 0) {
            var e = at + text.length
            while (e < sentence.length && e - at - text.length < 3 && sentence[e] in 'ぁ'..'ゖ') e++
            follow = sentence.substring(at + text.length, e)
        }
        for (n in follow.length downTo 1) out.add(text + follow.substring(0, n))
        val single = text.codePointCount(0, text.length) == 1
        if (!(single && follow.isNotEmpty())) out.add(text)
        var t = text
        while (t.length > 2) {
            t = t.substring(0, t.length - 1)
            if (t.any { KanjiText.isKanji(it.code) }) out.add(t)
        }
        return out.distinct()
    }
}
