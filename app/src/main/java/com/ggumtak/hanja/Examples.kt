package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject

/**
 * Short example sentences for the looked-up word: the sentence, the word as it appears in it (to highlight),
 * its reading in hiragana (particles as written, like furigana) and a Korean translation.
 */
data class Example(val ja: String, val word: String, val reading: String, val ko: String) {
    companion object {
        const val COUNT = 3

        /** English instructions, Korean translations in the answer. */
        const val SYSTEM_PROMPT = """You write example sentences for Korean speakers learning Japanese. The user sends a word and, when there is one, the sentence it came from.
Write 3 short, easy Japanese sentences that use the word in the same sense as in that sentence: natural everyday Japanese, about 25 characters each, JLPT N4-N3 vocabulary, each in a different situation.
- ja: the sentence.
- word: the word exactly as it appears in ja (its inflected form, character for character).
- reading: the whole sentence in hiragana with a space between phrases; write the particles は, へ, を as written, like furigana.
- ko: a natural Korean translation."""

        val SCHEMA: JSONObject
            get() {
                val item = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("ja", str("Japanese example sentence"))
                        .put("word", str("The word as written in ja"))
                        .put("reading", str("ja in hiragana, spaces between phrases, particles as written"))
                        .put("ko", str("Korean translation")))
                    .put("required", JSONArray().put("ja").put("word").put("reading").put("ko"))
                    .put("additionalProperties", false)
                return JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("examples", JSONObject().put("type", "array").put("items", item)))
                    .put("required", JSONArray().put("examples"))
                    .put("additionalProperties", false)
            }

        private fun str(description: String) = JSONObject().put("type", "string").put("description", description)

        fun userPrompt(word: String, sentence: String): String =
            if (sentence.isEmpty() || sentence == word) "Word: $word" else "Word: $word\nSentence: $sentence"

        private fun japanese(c: Char) = c in '぀'..'ヿ' || KanjiText.isKanji(c.code)

        private fun kanaReading(s: String) = s.isNotEmpty() && s.all { it in '぀'..'ヿ' || it == ' ' || it == '　' || it in "、。！？!?「」・ー〜…" }

        /** The examples in the model's JSON (at most [COUNT]); unusable ones are dropped. */
        fun parse(json: String): List<Example> {
            val arr = runCatching { JSONObject(json).optJSONArray("examples") }.getOrNull() ?: return emptyList()
            val out = ArrayList<Example>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ja = o.optString("ja").trim()
                val ko = o.optString("ko").trim()
                if (ja.none(::japanese) || ko.isEmpty()) continue
                val word = o.optString("word").trim().takeIf { it.isNotEmpty() && ja.contains(it) } ?: ""
                val reading = Readings.hira(o.optString("reading").trim()).takeIf(::kanaReading) ?: ""
                out.add(Example(ja, word, reading, ko))
                if (out.size == COUNT) break
            }
            return out
        }
    }
}
