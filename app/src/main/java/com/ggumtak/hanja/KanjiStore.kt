package com.ggumtak.hanja

import android.content.Context

/** assets/kanji.tsv (sorted by character), memory-mapped once per process off the main thread. */
object KanjiStore {
    private const val CACHE_SIZE = 256

    @Volatile
    private var tsv: SortedTsv? = null

    private val cache = object : LinkedHashMap<String, Kanji?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Kanji?>): Boolean = size > CACHE_SIZE
    }

    val loaded: Boolean get() = tsv != null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv == null) tsv = SortedTsv(mapAsset(ctx, "kanji.tsv"))
        }
    }

    fun get(ch: String): Kanji? {
        val t = tsv ?: return null
        synchronized(cache) {
            if (cache.containsKey(ch)) return cache[ch]
        }
        val parsed = t.find(ch)?.let { Kanji.parse(ch + "\t" + it) }
        synchronized(cache) { cache[ch] = parsed }
        return parsed
    }
}
