package com.ggumtak.hanja

import org.json.JSONArray
import org.json.JSONObject

/**
 * How the tapped word is read in its sentence, as Claude judged it: 今日 in 今日は雨ですが → きょう; per-kanji
 * parts unless the word is read as a whole (숙자훈). Only the pronunciation: the card shows it in place of the
 * dictionary's readings, in the same layout.
 */
data class ContextReading(
    val word: String,
    val reading: String,
    val whole: Boolean,
    val kanji: Map<String, String>,
    /** Each kanji's 훈음 for this word (台所의 所 → 곳 소). */
    val meanings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val SYSTEM_PROMPT = """너는 일본어 문장의 읽기를 판정하는 사전이다. 사용자는 문장과, 그 문장에서 찾을 글자를 보낸다.
그 글자가 들어 있는 단어를 문장에서 찾아(활용형은 문장에 나온 그대로, 조사는 빼고), 이 문장에서 실제로 읽히는 발음을 히라가나로 준다.
한자마다 이 문장에서 읽히는 부분을 히라가나로 나눠 준다. 숙자훈(今日 きょう, 部屋 へや처럼 한자별로 나눌 수 없는 읽기)이면 whole을 true로 하고 한자별 읽기는 빈 문자열로 둔다.
한자마다 meaning에는 이 단어 안에서 그 한자가 맡는 뜻을 한자 사전의 훈음 형식("훈 음", 음은 그 한자의 한국 한자음 한 글자)으로 쓴다. 훈은 이 단어에서의 뜻에 가장 자연스러운 것으로 고른다(예: 台所의 所 → 곳 소, 駅前의 前 → 앞 전, 貼る의 貼 → 붙일 첩, 駅의 駅 → 역 역). "붙이다 첩"처럼 동사형으로 쓰지 말고, 능·버금 같은 옛말도 쓰지 않는다."""

        /** The response schema (structured outputs: every object closed, every field required). */
        val SCHEMA: JSONObject
            get() = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("word", str("문장 속에서 그 글자가 들어 있는 단어, 문장에 나온 형태 그대로(조사 제외)"))
                    .put("reading", str("word의 이 문장 속 읽기, 히라가나"))
                    .put("whole", JSONObject().put("type", "boolean")
                        .put("description", "한자별로 나눌 수 없는 읽기(숙자훈)면 true"))
                    .put("kanji", JSONObject().put("type", "array").put("items", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("char", str("word 안의 한자 한 글자"))
                            .put("reading", str("이 문장에서 그 한자가 읽히는 부분, 히라가나. whole이면 빈 문자열"))
                            .put("meaning", str("이 단어 안에서 그 한자의 훈음, \"훈 음\" 형식(예: 곳 소, 붙일 첩)")))
                        .put("required", JSONArray().put("char").put("reading").put("meaning"))
                        .put("additionalProperties", false))))
                .put("required", JSONArray().put("word").put("reading").put("whole").put("kanji"))
                .put("additionalProperties", false)

        private fun str(description: String) = JSONObject().put("type", "string").put("description", description)

        fun userPrompt(target: String, sentence: String): String =
            "문장: ${sentence.ifEmpty { target }}\n찾을 글자: $target"

        private fun isReading(s: String): Boolean =
            s.isNotEmpty() && s.all { it in 'ぁ'..'ゟ' || it in '゠'..'ヿ' }

        /** The model's JSON, or null when it is not a usable answer (empty, not kana, a word without the target). */
        fun parse(json: String, target: String): ContextReading? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val word = o.optString("word").trim()
            val reading = Readings.hira(o.optString("reading").trim())
            if (word.isEmpty() || !isReading(reading)) return null
            if (target.isNotEmpty() && !word.contains(target) && !target.contains(word)) return null
            val whole = o.optBoolean("whole", false)
            val parts = LinkedHashMap<String, String>()
            val meanings = LinkedHashMap<String, String>()
            val arr = o.optJSONArray("kanji")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val k = arr.optJSONObject(i) ?: continue
                    val ch = k.optString("char").trim()
                    if (ch.codePointCount(0, ch.length) != 1) continue
                    val r = Readings.hira(k.optString("reading").trim())
                    if (!whole && isReading(r)) parts.putIfAbsent(ch, r)
                    val m = k.optString("meaning").trim()
                    if (m.isNotEmpty() && m.length <= 16 && m.none { it == '\n' }) meanings.putIfAbsent(ch, m)
                }
            }
            return ContextReading(word, reading, whole, parts, meanings)
        }
    }
}
