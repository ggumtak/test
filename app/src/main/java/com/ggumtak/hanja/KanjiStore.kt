package com.ggumtak.hanja

import android.content.Context

/** assets/kanji.tsv indexed by character, loaded once per process off the main thread. */
object KanjiStore {
    @Volatile
    private var lines: Map<String, String>? = null

    val loaded: Boolean get() = lines != null

    /** Blocking: call from a background thread. */
    fun load(ctx: Context) {
        if (lines != null) return
        synchronized(this) {
            if (lines != null) return
            val text = ctx.assets.open("kanji.tsv").use { it.readBytes().toString(Charsets.UTF_8) }
            lines = index(text)
        }
    }

    fun index(text: String): Map<String, String> {
        val map = HashMap<String, String>(16384)
        var start = 0
        while (start < text.length) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = text.length
            val tab = text.indexOf('\t', start)
            if (tab in (start + 1) until end) map.putIfAbsent(text.substring(start, tab), text.substring(start, end))
            start = end + 1
        }
        return map
    }

    fun get(ch: String): Kanji? = lines?.get(ch)?.let { Kanji.parse(it) }
}
