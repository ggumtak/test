package com.ggumtak.hanja

import android.content.Context

/**
 * assets/parts.tsv (radicals, parts and strokes), assets/origin.tsv (자원 and 외우기 for the 상용한자),
 * assets/similar.tsv (look-alikes), assets/radkanji.tsv (the kanji that share a radical) and assets/expand.tsv, all
 * packed by the build and memory-mapped (PackedTsv), and assets/radicals.tsv (the radical names, small, read once).
 * Loaded off the main thread.
 */
object PartsStore {
    @Volatile
    private var parts: Table? = null
    @Volatile
    private var origins: Table? = null
    @Volatile
    private var radicals: List<Radical> = emptyList()
    @Volatile
    private var similar: Table? = null
    @Volatile
    private var radkanji: Table? = null
    @Volatile
    private var expand: Table? = null

    /** Blocking (memory maps and a small file): call from a background thread. */
    fun load(ctx: Context) {
        if (parts != null) return
        synchronized(this) {
            if (parts != null) return
            radicals = runCatching {
                Radical.parseAll(ctx.assets.open("radicals.tsv").use { it.readBytes().toString(Charsets.UTF_8) })
            }.getOrDefault(emptyList())
            origins = runCatching { openTable(ctx, "origin") }.getOrNull()
            similar = runCatching { openTable(ctx, "similar") }.getOrNull()
            radkanji = runCatching { openTable(ctx, "radkanji") }.getOrNull()
            expand = runCatching { openTable(ctx, "expand") }.getOrNull()
            // 17 MB of strokes: only the blocks of the kanji being shown stay inflated.
            parts = openTable(ctx, "parts", keep = 16)
        }
    }

    fun parts(ch: String): Parts? = parts?.find(ch)?.let { Parts.parse(ch + "\t" + it) }

    /** The 유래 and 외우기: the 상용한자's, else those written for a radical or part the decks teach (Extra). */
    fun origin(ch: String): Origin? = origins?.find(ch)?.let { Origin.parse(it) } ?: KanjiStore.extra(ch)?.asOrigin()

    /** The 상용한자 that a radical or part outside them is seen in (厂: 原 反 圧), for its 쓰임 row; else empty. */
    fun usedIn(ch: String): List<String> = Similar.parse(KanjiStore.extra(ch)?.used)

    /**
     * The 상용한자 easily mistaken for [ch] by shape, the likeliest first (持: 特 侍 待), from assets/similar.tsv
     * (tools/build_similar_data.py); empty when it has none.
     */
    fun similar(ch: String): List<String> = Similar.parse(similar?.find(ch))

    /**
     * The 확장 row: up to three 상용한자 to learn with [ch] (the same sound part and reading, those with [ch] as their
     * sound part, the same sound part read otherwise, those with [ch] as a part), from assets/expand.tsv
     * (tools/build_expand_data.py); empty when it has none.
     */
    fun expand(ch: String): List<String> = Similar.parse(expand?.find(ch))

    /**
     * Other easy 상용한자 with the radical of [p] in the same shape and the same meaning (海: 池 汽 活 …), without
     * [ch] itself, from assets/radkanji.tsv; empty when there are none.
     */
    fun sameRadical(p: Parts, ch: String): List<String> =
        if (p.radical == 0 || p.form.isEmpty()) emptyList()
        else RadicalKanji.mates(radkanji?.find(RadicalKanji.key(p.radical, p.form)), ch)

    /** The plain meaning of a part's shape when it is a radical's (辶 → 가다·길), for the parts line. */
    fun meaningOf(form: String): String? = Radical.meaningOf(radicals, form)

    /** The name line for this character's radical (氵 left → さんずい 삼수변). */
    fun radical(p: Parts): Radical? = if (p.radical == 0) null else Radical.find(radicals, p.radical, p.form, p.position)
}

/** assets/usage.tsv (the readings worth learning and their common words, for the 상용한자), memory-mapped. */
object UsageStore {
    @Volatile
    private var tsv: Table? = null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv == null) tsv = openTable(ctx, "usage")
        }
    }

    fun get(ch: String): Usage? = tsv?.find(ch)?.let { Usage.parse(it) }
}
