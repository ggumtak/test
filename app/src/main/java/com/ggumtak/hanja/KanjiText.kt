package com.ggumtak.hanja

import java.net.URLEncoder

/** Pure helpers for the selected text: which characters are kanji, the dictionary links, the Claude prompt. */
object KanjiText {
    const val MAX_KANJI = 8
    const val MAX_SELECTION = 300

    fun isKanji(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3134F

    /** The distinct kanji of [text] in reading order, at most [limit]. */
    fun kanjiIn(text: String, limit: Int = MAX_KANJI): List<String> {
        val out = LinkedHashSet<String>()
        var i = 0
        while (i < text.length && out.size < limit) {
            val cp = text.codePointAt(i)
            if (isKanji(cp)) out.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out.toList()
    }

    /** One kun reading: the part the kanji stands for and the kana written after it (あか + るい). */
    data class Kun(val stem: String, val okurigana: String)

    /**
     * KANJIDIC's kun readings ("あか.るい・-あ.け・あ.く") as [Kun]s: affix forms ("-あ.け", "あ.け-") dropped
     * unless nothing else is left, duplicates removed, at most [limit].
     */
    fun kunReadings(kun: String, limit: Int = 5): List<Kun> {
        val all = kun.split('・').map { it.trim() }.filter { it.isNotEmpty() }
        val plain = all.filter { !it.startsWith("-") && !it.endsWith("-") }.ifEmpty { all }
        return plain.map { r ->
            val s = r.trim('-')
            val dot = s.indexOf('.')
            if (dot < 0) Kun(s, "") else Kun(s.substring(0, dot), s.substring(dot + 1).replace(".", ""))
        }.distinct().take(limit)
    }

    /** KANJIDIC's on readings ("メイ・ミョウ・ミン") as a list, at most [limit]. */
    fun onReadings(on: String, limit: Int = 4): List<String> =
        on.split('・').map { it.trim('-', ' ') }.filter { it.isNotEmpty() }.distinct().take(limit)

    /** The selection on one line, cut to [MAX_SELECTION] characters. */
    fun clean(text: CharSequence?): String {
        val s = text?.toString().orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (s.length <= MAX_SELECTION) s else s.substring(0, MAX_SELECTION)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun naverHanjaUrl(query: String): String = "https://hanja.dict.naver.com/#/search?query=" + enc(query)

    fun naverJapaneseUrl(query: String): String = "https://ja.dict.naver.com/#/search?query=" + enc(query)

    const val SYSTEM_PROMPT = """너는 한국어를 쓰는 일본어 학습자를 위한 한자 사전이다. 사용자는 다른 앱에서 선택한 글자를 보낸다.
한국어로 짧게 답한다. 마크다운 기호(#, *, 표, 굵게)는 쓰지 말고 일반 문장과 줄바꿈만 쓴다.
1. 한자마다 한 줄: 한자(한국 정자가 다르면 괄호에 정자) · 한국 훈음 · 일본어 음독/훈독 · 핵심 뜻.
2. 선택한 것이 단어나 문장이면 그 읽기(히라가나와 한글 발음)와 뜻을 한 줄로. 문장이 함께 오면 그 문장 속 뜻으로 설명한다.
3. 그 한자가 들어간 자주 쓰는 일본어 단어 2~3개를 "단어(읽기) 뜻" 형식으로.
확실하지 않은 내용은 추측하지 말고 모른다고 쓴다."""

    fun userPrompt(selection: String, sentence: String = ""): String =
        if (sentence.isEmpty() || sentence == selection) "선택한 글자: $selection" else "선택한 글자: $selection\n문장: $sentence"
}
