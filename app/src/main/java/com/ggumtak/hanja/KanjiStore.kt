package com.ggumtak.hanja

import android.content.Context

/**
 * assets/kanji.tsv (sorted by character) and assets/extra.tsv (the radicals and parts outside the 상용한자 that have
 * lines of their own, tools/extra), packed by the build and memory-mapped once per process off the main thread.
 */
object KanjiStore {
    @Volatile
    private var tsv: Table? = null
    @Volatile
    private var extras: Table? = null

    val loaded: Boolean get() = tsv != null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv != null) return
            extras = runCatching { openTable(ctx, "extra") }.getOrNull()
            tsv = openTable(ctx, "kanji")
        }
    }

    /** The character's line, with the 훈음 and 뜻 written for it when it is a radical or part ([Extra]). */
    fun get(ch: String): Kanji? {
        val k = tsv?.find(ch)?.let { Kanji.parse(ch + "\t" + it) }
        // A 상용한자 has its own (hunJa); the lines in extra.tsv are for characters outside them.
        if (k != null && k.hunJa.isNotEmpty()) return k
        return extra(ch)?.fill(ch, k) ?: k
    }

    fun extra(ch: String): Extra? = extras?.find(ch)?.let { Extra.parse(it) }
}
