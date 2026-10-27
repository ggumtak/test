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
2. 선택한 것이 단어나 문장이면 그 읽기(히라가나)와 뜻을 한 줄로.
3. 그 한자가 들어간 자주 쓰는 일본어 단어 2~3개를 "단어(읽기) 뜻" 형식으로.
확실하지 않은 내용은 추측하지 말고 모른다고 쓴다."""

    fun userPrompt(selection: String): String = "선택한 글자: $selection"
}
