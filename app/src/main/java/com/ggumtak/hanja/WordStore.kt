package com.ggumtak.hanja

import android.content.Context

/**
 * assets/words.tsv (tools/build_word_data.py): JMdict's written forms and their common readings, kept as the raw
 * UTF-8 bytes and binary-searched (the file is sorted by bytes), loaded once per process off the main thread.
 */
object WordStore {
    @Volatile
    private var index: WordIndex? = null

    /** Blocking: call from a background thread. */
    fun load(ctx: Context) {
        if (index != null) return
        synchronized(this) {
            if (index != null) return
            index = WordIndex(ctx.assets.open("words.tsv").use { it.readBytes() })
        }
    }

    fun readings(word: String): List<String> = index?.find(word).orEmpty()
}

/** Binary search over sorted "form\treading・reading\n" lines. */
class WordIndex(private val data: ByteArray) {
    private val starts: IntArray = run {
        var n = 0
        for (b in data) if (b == '\n'.code.toByte()) n++
        val a = IntArray(n + 1)
        var k = 0
        a[k++] = 0
        for (i in data.indices) if (data[i] == '\n'.code.toByte() && i + 1 < data.size) a[k++] = i + 1
        a.copyOf(k)
    }

    fun find(word: String): List<String>? {
        if (word.isEmpty()) return null
        val key = word.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(key, starts[mid])
            when {
                c == 0 -> return readingsAt(starts[mid] + key.size + 1)
                c < 0 -> hi = mid - 1
                else -> lo = mid + 1
            }
        }
        return null
    }

    /** [key] against the form starting at [start] (ending at the tab), as unsigned bytes. */
    private fun compare(key: ByteArray, start: Int): Int {
        var i = 0
        while (true) {
            val p = start + i
            val formEnded = p >= data.size || data[p] == '\t'.code.toByte()
            if (i == key.size) return if (formEnded) 0 else -1
            if (formEnded) return 1
            val d = (key[i].toInt() and 0xFF) - (data[p].toInt() and 0xFF)
            if (d != 0) return d
            i++
        }
    }

    private fun readingsAt(from: Int): List<String> {
        var end = from
        while (end < data.size && data[end] != '\n'.code.toByte()) end++
        return String(data, from, end - from, Charsets.UTF_8).split('・').filter { it.isNotEmpty() }
    }
}
