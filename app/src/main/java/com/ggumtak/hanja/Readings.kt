package com.ggumtak.hanja

/**
 * Which reading each kanji of a word takes (電気 でんき → でん, き), so the card can say "in this word" next to the
 * character's general readings. Sound changes are allowed: voicing in the middle of a word (人々 ひとびと), a
 * small っ for the last kana (学校 がっこう), 々 repeating the kanji before it. A word read as a whole
 * (部屋 へや, 今日 きょう) does not split: [split] returns null.
 */
object Readings {
    private val GODAN_I = mapOf(
        'う' to 'い', 'く' to 'き', 'ぐ' to 'ぎ', 'す' to 'し',
        'つ' to 'ち', 'ぬ' to 'に', 'ぶ' to 'び', 'む' to 'み', 'る' to 'り',
    )
    private const val ICHIDAN_VOWELS = "いきぎしじちぢにひびぴみりえけげせぜてでねへべぺめれ"
    private val KA_KE = listOf("か", "が", "け")

    private fun ichidan1Mora(stem: String, kun: String): Boolean = when (stem) {
        "み", "で", "に", "ひ" -> true
        "き" -> "き.せる" in kun
        "ね" -> "ね.かす" in kun
        "へ" -> "へ.らす" !in kun
        "え" -> "えら.ぶ" !in kun
        "い" -> "い.れる" !in kun && "かなめ" !in kun && "せん.じる" !in kun
        else -> false
    }

    /** KANJIDIC's readings of a kanji as hiragana candidates: on, kun stems, kun with okurigana, verb ren'yōkei. */
    fun candidates(k: Kanji): List<String> {
        val out = LinkedHashSet<String>()
        for (on in k.on.split('・')) on.trim('-', ' ').takeIf { it.isNotEmpty() }?.let { out.add(hira(it)) }
        for (kun in k.kun.split('・')) {
            val s = kun.trim('-', ' ')
            if (s.isEmpty()) continue
            val stem = s.substringBefore('.')
            out.add(stem)
            out.add(s.replace(".", ""))
            val dot = s.indexOf('.')
            if (dot >= 0) {
                val oku = s.substring(dot + 1).replace(".", "")
                if (oku.length == 1) {
                    if (oku[0] != 'る' || !ichidan1Mora(stem, k.kun)) {
                        GODAN_I[oku[0]]?.let { out.add(stem + it) }
                    }
                } else if (oku.length >= 2 && oku.last() == 'る' && oku[oku.length - 2] in ICHIDAN_VOWELS) {
                    out.add(stem + oku.dropLast(1))
                }
            }
        }
        return out.toList()
    }

    fun hira(s: String): String = String(CharArray(s.length) { i ->
        when (val c = s[i]) {
            in 'ァ'..'ヶ' -> c - 0x60
            'ヽ' -> 'ゝ'
            'ヾ' -> 'ゞ'
            else -> c
        }
    })

    private const val VOICED_FROM = "かきくけこさしすせそたちつてとはひふへほ"
    private const val VOICED_TO = "がぎぐげござじずぜぞだぢづでどばびぶべぼ"
    private const val HALF_TO = "ぱぴぷぺぽ"

    /** [r] and its sound-changed forms: voiced first kana (not at the word's start), っ for a final つくちきり. */
    private fun variants(r: String, wordStart: Boolean): List<String> {
        val out = ArrayList<String>(6)
        out.add(r)
        if (!wordStart && r.isNotEmpty()) {
            val i = VOICED_FROM.indexOf(r[0])
            if (i >= 0) {
                out.add(VOICED_TO[i] + r.substring(1))
                if (i >= 15) out.add(HALF_TO[i - 15] + r.substring(1))
            }
            if (r[0] == 'ち') out.add("じ" + r.substring(1))
            if (r[0] == 'つ') out.add("ず" + r.substring(1))
        }
        for (v in out.toList()) if (v.length >= 2 && v.last() in "つくちきり") out.add(v.dropLast(1) + "っ")
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
        // A failed suffix stays failed regardless of the path taken to reach it.
        val failed = HashSet<Pair<Int, Int>>()
        val options = chars.mapIndexed { ci, ch ->
            if (ch == "ヶ" || ch == "ヵ") KA_KE
            else {
                val base = if (ch == "々") chars.getOrNull(ci - 1) else ch
                base?.let(readingsOf).orEmpty().flatMap { variants(it, ci == 0) }
                    .distinct().sortedByDescending { it.length }
            }
        }
        fun go(ci: Int, ri: Int): Boolean {
            if (ci == chars.size) return ri == r.length
            if ((ci to ri) in failed) return false
            val ch = chars[ci]
            val cp = ch.codePointAt(0)
            if (!KanjiText.isKanji(cp) && ch != "々" && ch != "ヶ" && ch != "ヵ") {
                val h = hira(ch)
                if (!r.startsWith(h, ri)) return false
                out[ci] = h
                return go(ci + 1, ri + h.length)
            }
            for (t in options[ci]) {
                if (t.isNotEmpty() && r.startsWith(t, ri)) {
                    out[ci] = t
                    if (go(ci + 1, ri + t.length)) return true
                }
            }
            failed.add(ci to ri)
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
        while (t.codePointCount(0, t.length) > 2) {
            t = t.substring(0, t.offsetByCodePoints(t.length, -1))
            if (KanjiText.kanjiIn(t).isNotEmpty()) out.add(t)
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

    /** The hiragana right after [text] in [sentence] (at most [max]): its okurigana and what follows. */
    fun following(text: String, sentence: String, max: Int = 6): String {
        val at = if (sentence.isEmpty() || text.isEmpty()) -1 else sentence.indexOf(text)
        if (at < 0) return ""
        var e = at + text.length
        while (e < sentence.length && e - at - text.length < max && sentence[e] in '\u3041'..'\u3096') e++
        return sentence.substring(at + text.length, e)
    }

    /** How a word-final kana changes when the word is inflected (貼る → 貼って, 書く → 書いた, 暖かい → 暖かく). */
    private val INFLECT = mapOf(
        'う' to "うわいえおっ", 'く' to "くかきけこい", 'ぐ' to "ぐがぎげごい", 'す' to "すさしせそ",
        'つ' to "つたちてとっ", 'ぬ' to "ぬなにねのん", 'ぶ' to "ぶばびべぼん", 'む' to "むまみめもん",
        'る' to "るらりれろっ", 'い' to "いかくけさそ",
    )

    private val ICHIDAN_REST = listOf(
        "ます", "まし", "ませ", "た", "て", "ない", "なか", "なく", "なけ",
        "よう", "られ", "させ", "たい", "たく", "たら", "たり", "ず", "ぬ", "る", "れ", "ろ",
    )

    /**
     * The kun reading a single kanji takes before the kana [follow] in its sentence (貼 + "ってあります" → は of
     * 貼る; 暖 + "かくなかった" → あたた of 暖かい), from its KANJIDIC kun readings: the okurigana must match,
     * its last kana in any inflected form (an ichidan る may drop: 食べ + ます). Longer okurigana win.
     */
    fun kunBefore(kun: String, follow: String): KanjiText.Kun? {
        if (follow.isEmpty()) return null
        val cands = KanjiText.kunReadings(kun, limit = 20).filter { it.okurigana.isNotEmpty() }
            .sortedByDescending { it.okurigana.length }
        for (k in cands) {
            val head = k.okurigana.dropLast(1)
            val last = k.okurigana.last()
            if (!follow.startsWith(head)) continue
            val rest = follow.substring(head.length)
            if (last == 'る' && head.isNotEmpty() && head.last() in ICHIDAN_VOWELS) {
                if (head !in listOf("に", "で", "へ") || ICHIDAN_REST.any { rest.startsWith(it) }) return k
                continue
            }
            if (last == 'る' && head.isEmpty() && ichidan1Mora(k.stem, kun)) {
                if (ICHIDAN_REST.any { rest.startsWith(it) } &&
                    !rest.startsWith("ずる") && !rest.startsWith("てば") && !rest.startsWith("たぬ")) return k
                continue
            }
            if (rest.isEmpty()) continue
            if ((k.stem == "い" || k.stem == "ゆ") && k.okurigana == "く" && rest.startsWith("っ")) return k
            val c = rest[0]
            val forms = INFLECT[last] ?: last.toString()
            if (c !in forms) continue
            if (c in "こごそとほぼもろおの" && (rest.length < 2 || rest[1] != 'う')) continue
            if (rest.startsWith("から") || rest.startsWith("まで") || rest.startsWith("しか")) continue
            return k
        }
        return null
    }
}
