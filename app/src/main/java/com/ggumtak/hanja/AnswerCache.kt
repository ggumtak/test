package com.ggumtak.hanja

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Cache keys and the legacy SharedPreferences format; pure, so the JVM tests cover it. */
object AnswerCache {
    /** "version:sha256", also the file name; keys never contain card text. */
    fun key(version: String, target: String, sentence: String): String {
        val input = "${target.length}:$target${sentence.length}:$sentence"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return version + ":" + bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** The answer and its time in a legacy SharedPreferences value ({"time":…,"answer":…}), or null. */
    fun legacy(value: Any?): Pair<String, Long>? = runCatching {
        val o = JSONObject(value as String)
        o.getString("answer") to o.optLong("time", 0L)
    }.getOrNull()
}

/**
 * Answers kept as one small file per key in [dir]: a hit reads one file and a write writes one, however many
 * are kept (SharedPreferences rewrote and re-parsed the whole multi-megabyte XML instead). Over [limit] the
 * least recently used tenth goes: a hit refreshes the file's time, so cards under review stay. Blocking file
 * I/O: call from background threads.
 */
class AnswerStore(private val dir: File, private val limit: Int) {
    private fun file(key: String) = File(dir, key.replace(':', '_'))

    fun get(key: String): String? {
        val f = file(key)
        val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        f.setLastModified(System.currentTimeMillis())
        return text
    }

    /** [time]: the answer's age for eviction (0: now). [evict]: false while moving many in; call [trim] after. */
    @Synchronized
    fun put(key: String, answer: String, time: Long = 0L, evict: Boolean = true) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val target = file(key)
        val fresh = !target.exists()
        val tmp = File(dir, target.name + ".tmp")
        runCatching {
            tmp.writeText(answer, Charsets.UTF_8)
            if (time > 0) tmp.setLastModified(time)
            if (!tmp.renameTo(target)) tmp.delete()
        }
        if (fresh && evict) trim()
    }

    fun size(): Int = dir.list()?.size ?: 0

    @Synchronized
    fun trim() {
        val names = dir.list() ?: return
        if (names.size <= limit) return
        val files = dir.listFiles() ?: return
        files.map { it to it.lastModified() }.sortedWith(compareBy<Pair<File, Long>> { it.second }.thenBy { it.first.name })
            .take(files.size - limit + limit / 10).forEach { it.first.delete() }
    }
}
