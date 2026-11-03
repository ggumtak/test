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
        return entries.keys.sortedWith(compareBy<String> { key ->
            runCatching { JSONObject(entries[key] as String).optLong("time", 0) }.getOrDefault(0)
        }.thenBy { it }).take(maxOf(1, entries.size - limit + limit / 10))
    }
}
