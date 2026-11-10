package com.ggumtak.hanja

/**
 * Kana → Hangul as it sounds (メイ → 메-, しょう → 쇼-, がっこう → 갓코-, コーヒー → 코-히-): a reading aid,
 * not the official loanword spelling. ん and っ become a final consonant (ㄴ, ㅅ) on the syllable before them;
 * characters that are not kana are kept as they are.
 *
 * A long vowel is written "-", as ー already was: こう is said [koː], and "코우" taught a Korean reader to say
 * the う (the classic mistake). Long: お段+う/お, う段+う, え段+い/え, あ段+あ, い段+い, inside one stretch of
 * reading. Not across a boundary the caller knows of ([convertParts]): 思う is おも + う and 子牛 is こ + うし,
 * where the う is said.
 */
object KanaHangul {
    private val single: Map<Char, String> = run {
        val rows = listOf(
            "あいうえお" to "아이우에오", "かきくけこ" to "카키쿠케코", "がぎぐげご" to "가기구게고",
            "さしすせそ" to "사시스세소", "ざじずぜぞ" to "자지즈제조", "たちつてと" to "타치츠테토",
            "だぢづでど" to "다지즈데도", "なにぬねの" to "나니누네노", "はひふへほ" to "하히후헤호",
            "ばびぶべぼ" to "바비부베보", "ぱぴぷぺぽ" to "파피푸페포", "まみむめも" to "마미무메모",
            "やゆよ" to "야유요", "らりるれろ" to "라리루레로", "わゐゑを" to "와이에오",
            "ぁぃぅぇぉ" to "아이우에오", "ゃゅょ" to "야유요", "ゎ" to "와", "ゔ" to "부",
        )
        val m = HashMap<Char, String>()
        for ((kana, hangul) in rows) kana.forEachIndexed { i, c -> m[c] = hangul[i].toString() }
        m
    }

    /** The vowel each kana ends in (か → a, ょ → o), for telling a long vowel. */
    private val vowels: Map<Char, Char> = run {
        val m = HashMap<Char, Char>()
        val five = listOf("あいうえお", "かきくけこ", "がぎぐげご", "さしすせそ", "ざじずぜぞ", "たちつてと", "だぢづでど",
            "なにぬねの", "はひふへほ", "ばびぶべぼ", "ぱぴぷぺぽ", "まみむめも", "らりるれろ", "ぁぃぅぇぉ")
        for (row in five) row.forEachIndexed { i, c -> m[c] = "aiueo"[i] }
        "やゆよ".forEachIndexed { i, c -> m[c] = "auo"[i] }
        "ゃゅょ".forEachIndexed { i, c -> m[c] = "auo"[i] }
        m['わ'] = 'a'; m['ゎ'] = 'a'; m['を'] = 'o'; m['ゔ'] = 'u'
        m
    }

    /** Whether [c] after a syllable ending in [vowel] only lengthens it (こ + う, せ + い, お + お). */
    private fun lengthens(vowel: Char, c: Char): Boolean = when (vowel) {
        'o' -> c == 'う' || c == 'お'
        'u' -> c == 'う'
        'e' -> c == 'い' || c == 'え'
        'a' -> c == 'あ'
        'i' -> c == 'い'
        else -> false
    }

    /**
     * Verbs whose final う is said, when written in kana alone, where no kanji shows the boundary (おも + う). Only
     * the ones that are not also another common word with a long vowel (そう, よう, こう, いこう 行こう are left out).
     */
    private val U_VERBS = setOf("あらそう", "うるおう", "おおう", "おそう", "おもう", "かよう", "きそう", "くるう", "さそう",
        "すくう", "そろう", "ただよう", "つどう", "ととのう", "におう", "ぬう", "ぬぐう", "ひろう", "ふるう", "まどう", "まよう",
        "やとう", "よそおう")

    /** Two-kana sounds: きゃ, しょ, ふぁ, てぃ … */
    private val pairs: Map<String, String> = run {
        val m = HashMap<String, String>()
        val yoon = mapOf(
            'き' to "캬큐쿄", 'ぎ' to "갸규교", 'し' to "샤슈쇼", 'じ' to "자주조", 'ち' to "차추초", 'ぢ' to "자주조",
            'に' to "냐뉴뇨", 'ひ' to "햐휴효", 'び' to "뱌뷰뵤", 'ぴ' to "퍄퓨표", 'み' to "먀뮤묘", 'り' to "랴류료",
        )
        for ((k, v) in yoon) "ゃゅょ".forEachIndexed { i, s -> m["$k$s"] = v[i].toString() }
        val extra = mapOf(
            "ふぁ" to "화", "ふぃ" to "피", "ふぇ" to "페", "ふぉ" to "포", "てぃ" to "티", "でぃ" to "디",
            "とぅ" to "투", "どぅ" to "두", "うぃ" to "위", "うぇ" to "웨", "うぉ" to "워", "ゔぁ" to "바",
            "ゔぃ" to "비", "ゔぇ" to "베", "ゔぉ" to "보", "しぇ" to "셰", "じぇ" to "제", "ちぇ" to "체",
            "つぁ" to "차", "つぃ" to "치", "つぇ" to "체", "つぉ" to "초", "いぇ" to "예", "くぁ" to "콰",
        )
        m.putAll(extra)
        m
    }

    private fun toHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') c - 0x60 else c

    fun isKana(c: Char): Boolean = c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー'

    /** True when [text] has kana and nothing but kana, spaces and punctuation (no kanji, no Latin). */
    fun kanaOnly(text: String): Boolean =
        text.any { isKana(it) } && text.all { isKana(it) || it.isWhitespace() || it in "・、。！？!?「」〜~" }

    /** Endings whose final は is the particle, read わ: こんにちは, こんばんは, では, には, とは. */
    private val WA_ENDINGS = listOf("こんにちは", "こんばんは", "では", "には", "とは")

    /** [kana] as one stretch of reading (an on reading, a kun stem, a word read as a whole, a kana word). */
    fun convert(kana: String): String {
        val t = kana.trim()
        return if (t in U_VERBS) render(listOf(t.dropLast(1), "う")) else render(listOf(kana))
    }

    /**
     * The stretches of a reading in order (おも, う; こ, うし; がっ, こう): no long vowel is made across two of them,
     * while ん and っ still close the syllable before them.
     */
    fun convertParts(parts: List<String>): String = render(parts)

    /**
     * The Hangul for a word's [reading] when each character's reading is known ([split], one per character of
     * [form], as Readings.split gives it): each kanji is a stretch of its own and the kana between kanji are one
     * (大きい: おお, きい → 오-키-; 思う: おも, う → 오모우). Without a split, [reading] as one stretch.
     */
    fun ofWord(form: String, split: List<String>?, reading: String): String {
        if (split == null) return convert(reading)
        val parts = ArrayList<String>()
        var kanaRun = false
        var i = 0
        var n = 0
        while (i < form.length && n < split.size) {
            val cp = form.codePointAt(i)
            val kana = !KanjiText.isKanji(cp) && cp != '々'.code
            if (kana && kanaRun) parts[parts.size - 1] = parts[parts.size - 1] + split[n] else parts.add(split[n])
            kanaRun = kana
            i += Character.charCount(cp)
            n++
        }
        return if (i < form.length || n < split.size) convert(reading) else convertParts(parts)
    }

    private fun render(parts: List<String>): String {
        val out = StringBuilder()
        val s = parts.joinToString("").map { toHiragana(it) }.toMutableList()
        val joined = s.joinToString("")
        if (WA_ENDINGS.any { joined.endsWith(it) }) s[s.size - 1] = 'わ'
        val starts = HashSet<Int>()
        var at = 0
        for (p in parts) {
            starts.add(at)
            at += p.length
        }
        // The vowel the last syllable ends in, while a long vowel may still follow it.
        var vowel = ' '
        var i = 0
        while (i < s.size) {
            val c = s[i]
            if (i in starts) vowel = ' '
            val pair = if (i + 1 < s.size && (i + 1) !in starts) pairs["$c${s[i + 1]}"] else null
            when {
                pair != null -> {
                    out.append(pair)
                    vowel = vowels[s[i + 1]] ?: ' '
                    i += 2
                    continue
                }
                c == 'ん' -> { addFinal(out, 4, "ㄴ"); vowel = ' ' }
                c == 'っ' -> { addFinal(out, 19, "ㅅ"); vowel = ' ' }
                c == 'ー' -> { out.append('-'); vowel = ' ' }
                lengthens(vowel, c) -> { out.append('-'); vowel = ' ' }
                else -> {
                    out.append(single[c] ?: c.toString())
                    vowel = vowels[c] ?: ' '
                }
            }
            i++
        }
        return out.toString()
    }

    /** Puts final consonant [jong] (index in the Unicode syllable table) on the last syllable, else [alone]. */
    private fun addFinal(out: StringBuilder, jong: Int, alone: String) {
        val last = out.lastOrNull()
        if (last != null && last in '가'..'힣' && (last - '가') % 28 == 0) {
            out.setCharAt(out.length - 1, last + jong)
        } else {
            out.append(alone)
        }
    }
}
