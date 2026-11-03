package com.ggumtak.hanja

import org.json.JSONObject
import java.security.MessageDigest

/** Pure cache policy; private storage and background I/O are handled by Prefs. */
object AnswerCache {
    fun key(version: String, target: String, sentence: String): String {
        val input = "${target.length}:$target${sentence.length}:$sentence"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return version + ":" + bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun pack(json: String, time: Long = System.currentTimeMillis()): String =
        JSONObject().put("time", time).put("answer", json).toString()

    fun answer(value: String): String? =
        runCatching { JSONObject(value).getString("answer") }.getOrNull()

    fun evictions(entries: Map<String, *>, limit: Int): List<String> {
        if (entries.size < limit) return emptyList()
        return entries.map { (key, value) ->
            key to runCatching { JSONObject(value as String).optLong("time", Long.MIN_VALUE) }
                .getOrDefault(Long.MIN_VALUE)
        }.sortedWith(compareBy<Pair<String, Long>> { it.second }.thenBy { it.first })
            .take(maxOf(1, entries.size - limit + limit / 10)).map { it.first }
    }
}
