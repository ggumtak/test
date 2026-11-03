package com.ggumtak.hanja

import android.content.Context

/** assets/kanji.tsv (sorted by character), memory-mapped once per process off the main thread. */
object KanjiStore {
    @Volatile
    private var tsv: SortedTsv? = null

    val loaded: Boolean get() = tsv != null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv == null) tsv = SortedTsv(mapAsset(ctx, "kanji.tsv"))
        }
    }

    fun get(ch: String): Kanji? = tsv?.find(ch)?.let { Kanji.parse(ch + "\t" + it) }
}
