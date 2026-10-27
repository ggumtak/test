package com.ggumtak.hanja

/**
 * Kana → Hangul as it sounds (メイ → 메이, しょう → 쇼우, がっこう → 갓코우, コーヒー → 코-히-): a reading aid,
 * not the official loanword spelling. ん and っ become a final consonant (ㄴ, ㅅ) on the syllable before them;
 * characters that are not kana are kept as they are.
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

    fun convert(kana: String): String {
        val out = StringBuilder()
        val s = kana.map { toHiragana(it) }
        var i = 0
        while (i < s.size) {
            val c = s[i]
            val pair = if (i + 1 < s.size) pairs["$c${s[i + 1]}"] else null
            when {
                pair != null -> { out.append(pair); i += 2; continue }
                c == 'ん' -> addFinal(out, 4, "ㄴ")
                c == 'っ' -> addFinal(out, 19, "ㅅ")
                c == 'ー' -> out.append('-')
                else -> out.append(single[c] ?: c.toString())
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
