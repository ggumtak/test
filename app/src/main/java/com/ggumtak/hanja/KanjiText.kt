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
     * The look of every answer shown on the card (Rich renders it): Japanese readings in parentheses, which the
     * app follows with the Hangul pronunciation, and a few marks the card styles.
     */
    private const val FORMAT = """Formatting (the app renders it): write a Japanese reading in parentheses right after the kanji, in hiragana: 電気(でんき). The app adds the Hangul pronunciation itself, so never write Hangul pronunciations or romaji. Section labels go in 【】 at the start of a line, list items start with "- ", a Korean translation line starts with "→ ", and one key term may be bold with **…**. No other Markdown: no #, tables or nested lists."""

    /**
     * 'Claude 설명': what the card above does not already show, in short labelled sections that are quick to
     * scan and help the word stick (the meaning here, a reading worth noticing, a memory story from the kanji's
     * parts, the link to the Korean 한자어, common words). English instructions, Korean answer.
     */
    const val SYSTEM_PROMPT = """You are a friendly kanji tutor for Korean speakers learning Japanese. The user sends text they selected (a kanji, a word or a sentence), sometimes with the sentence it came from. The card above your answer already shows each kanji's Korean 훈음, on/kun readings, stroke count and basic meaning: do not repeat those. Add what helps the learner understand and remember.
Write in friendly, natural Korean (해요체), short and easy to scan on a phone. Use these sections in this order, each starting a new line with its label; skip a section when there is nothing worth saying:
【뜻】 What it means here, in one or two sentences; when a sentence is given, its meaning in that sentence.
【읽기】 Only something worth noticing about how it is read here: a sound change, a whole-word reading, a common misreading.
【외우기】 A short, vivid way to remember the kanji: its parts and a one-line story tying them to the meaning; say so when a part gives the sound. A made-up story is a memory aid, never presented as real etymology.
【한국어 연결】 A Korean 한자어 with the same kanji and how its Korean 한자음 relates to the Japanese reading (학교 → 学校(がっこう)). Only when the link is real and visible; it is a tendency, not a rule.
【자주 쓰는 말】 2-3 common words or phrases, one per line: "- 単語(たんご) 뜻".
""" + FORMAT + """
About 10 short lines at most. No greeting, preamble or closing. If you are not sure of something, leave it out or say you don't know instead of guessing."""

    /** The 질문 chat: a tutor that answers what the user typed after the dragged text, in the same look. */
    fun chatSystemPrompt(selection: String, sentence: String): String {
        val base = """You are a friendly Japanese tutor for a Korean speaker. The user picks part of a sentence they are studying and asks about it.
Answer in friendly, natural Korean (해요체), even when the question is written in Japanese or English. Make it easy to scan on a phone:
- First the direct answer in one or two sentences; the key point may be bold.
- Then only the explanation it needs, as at most 3 short "- " lines. For grammar, show the form (동사 て형 + いる).
- When it helps, one short example: the Japanese sentence on one line, its Korean translation on the next.
- Say where Korean works the same way (は/が and 은는/이가), and warn where Korean would mislead.
Match the length to the question: a quick question gets a quick answer. 【】 labels only for long answers.
""" + FORMAT + """
No greeting or preamble. If you are not sure, say you don't know instead of guessing."""
        val ctx = if (sentence.isNotEmpty() && sentence != selection) "\n\nSelected: $selection\nSentence: $sentence"
        else if (selection.isNotEmpty()) "\n\nSelected: $selection" else ""
        return base + ctx
    }

    fun userPrompt(selection: String, sentence: String = ""): String =
        if (sentence.isEmpty() || sentence == selection) "Selected: $selection" else "Selected: $selection\nSentence: $sentence"
}
