package com.ggumtak.hanja

import android.content.Context

/**
 * assets/parts.tsv (radicals, parts and KanjiVG strokes, memory-mapped), assets/origin.tsv (자원 and 외우기 for the
 * 상용한자, memory-mapped), assets/similar.tsv (look-alikes) and assets/radkanji.tsv (the kanji that share a
 * radical), both memory-mapped, and assets/radicals.tsv (the radical names, small, read once). Loaded off the main thread.
 */
object PartsStore {
    @Volatile
    private var parts: SortedTsv? = null
    @Volatile
    private var origins: SortedTsv? = null
    @Volatile
    private var radicals: List<Radical> = emptyList()
    @Volatile
    private var similar: SortedTsv? = null
    @Volatile
    private var radkanji: SortedTsv? = null

    /** Blocking (memory maps and a small file): call from a background thread. */
    fun load(ctx: Context) {
        if (parts != null) return
        synchronized(this) {
            if (parts != null) return
            radicals = runCatching {
                Radical.parseAll(ctx.assets.open("radicals.tsv").use { it.readBytes().toString(Charsets.UTF_8) })
            }.getOrDefault(emptyList())
            origins = runCatching { SortedTsv(mapAsset(ctx, "origin.tsv")) }.getOrNull()
            similar = runCatching { SortedTsv(mapAsset(ctx, "similar.tsv")) }.getOrNull()
            radkanji = runCatching { SortedTsv(mapAsset(ctx, "radkanji.tsv")) }.getOrNull()
            parts = SortedTsv(mapAsset(ctx, "parts.tsv"))
        }
    }

    fun parts(ch: String): Parts? = parts?.find(ch)?.let { Parts.parse(ch + "\t" + it) }

    fun origin(ch: String): Origin? = origins?.find(ch)?.let { Origin.parse(it) }

    /**
     * The 상용한자 easily mistaken for [ch] by shape, the likeliest first (持: 特 侍 待), from assets/similar.tsv
     * (tools/build_similar_data.py); empty when it has none.
     */
    fun similar(ch: String): List<String> = Similar.parse(similar?.find(ch))

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
    private var tsv: SortedTsv? = null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv == null) tsv = SortedTsv(mapAsset(ctx, "usage.tsv"))
        }
    }

    fun get(ch: String): Usage? = tsv?.find(ch)?.let { Usage.parse(it) }
}
