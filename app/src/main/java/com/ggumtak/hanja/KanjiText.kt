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
        return if (s.codePointCount(0, s.length) <= MAX_SELECTION) s
        else s.substring(0, s.offsetByCodePoints(0, MAX_SELECTION))
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun naverHanjaUrl(query: String): String = "https://hanja.dict.naver.com/#/search?query=" + enc(query)

    fun naverJapaneseUrl(query: String): String = "https://ja.dict.naver.com/#/search?query=" + enc(query)

    /**
     * 'Claude 설명'. The instructions are English (fewer tokens than Korean, so the answer starts sooner); the
     * answer is Korean, short and in plain text, because the card shows it as is.
     */
    const val SYSTEM_PROMPT = """You are a kanji dictionary for Korean speakers learning Japanese. The user sends text they selected in another app, sometimes with the sentence it came from.
Always answer in natural, polite Korean, briefly, with no preamble or closing remarks. Plain text and line breaks only: no Markdown (#, *, tables, bold).
1. One line per kanji: the kanji (the Korean traditional form in parentheses if it differs) · Korean 훈음 · Japanese on/kun readings · core meaning.
2. If the selection is a word or a sentence, one line with its reading (hiragana and Hangul pronunciation) and meaning; when a sentence is given, its meaning in that sentence.
3. 2-3 common Japanese words with the kanji, as "word(reading) meaning".
If you are not sure about something, say you don't know instead of guessing."""

    /** The 질문 chat: a tutor that answers what the user typed after the dragged text. */
    fun chatSystemPrompt(selection: String, sentence: String): String {
        val base = """You are a tutor for a Korean speaker learning Japanese. The user picks part of a sentence they are studying and asks about it.
Always answer in natural, polite Korean, even when the question is written in Japanese or English. Be concise: the direct answer first, then only the explanation it needs, no preamble. Plain text and line breaks only: no Markdown (#, *, tables, bold).
When you write a Japanese reading, give the hiragana and the Hangul pronunciation together. If you are not sure, say you don't know instead of guessing."""
        val ctx = if (sentence.isNotEmpty() && sentence != selection) "\n\nSelected: $selection\nSentence: $sentence"
        else if (selection.isNotEmpty()) "\n\nSelected: $selection" else ""
        return base + ctx
    }

    fun userPrompt(selection: String, sentence: String = ""): String =
        if (sentence.isEmpty() || sentence == selection) "Selected: $selection" else "Selected: $selection\nSentence: $sentence"
}
