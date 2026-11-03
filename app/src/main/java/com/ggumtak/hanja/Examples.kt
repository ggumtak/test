package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject

/**
 * Short example sentences for the looked-up word: the sentence, the word as it appears in it (to highlight),
 * its reading in hiragana as pronounced (particles は/へ/を written わ/え/お, so the Hangul comes out right) and a
 * Korean translation.
 */
data class Example(val ja: String, val word: String, val reading: String, val ko: String) {
    companion object {
        const val COUNT = 3

        const val SYSTEM_PROMPT = """너는 한국어를 쓰는 일본어 학습자에게 예문을 주는 사전이다. 사용자는 단어와, 그 단어가 나온 원래 문장(있으면)을 보낸다.
그 단어를 원래 문장과 같은 뜻으로 쓴 짧고 쉬운 일본어 예문 3개를 만든다. 일상에서 자연스럽게 쓰는 문장으로, 한 문장에 25자 안팎, JLPT N4~N3 수준의 쉬운 말을 쓴다. 3개는 서로 다른 상황으로 한다.
word에는 예문 속에 그 단어가 실제로 쓰인 모양을 그대로 적는다(활용형 그대로, 예문 안의 글자와 정확히 같게).
reading에는 예문 전체를 실제 발음대로 히라가나로 적고, 말마디 사이를 띄어 쓴다. 조사 は는 わ, へ는 え, を는 お로 적는다.
ko에는 자연스러운 한국어 번역을 적는다."""

        val SCHEMA: JSONObject
            get() {
                val item = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("ja", str("일본어 예문"))
                        .put("word", str("예문 속에서 그 단어가 쓰인 모양, 예문 글자 그대로"))
                        .put("reading", str("예문 전체의 실제 발음, 히라가나, 말마디 사이 띄어쓰기"))
                        .put("ko", str("한국어 번역")))
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
            if (sentence.isEmpty() || sentence == word) "단어: $word" else "단어: $word\n원래 문장: $sentence"

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
