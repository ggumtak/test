package com.ggumtak.hanja

import android.content.Context

/**
 * assets/parts.tsv (radicals, parts and KanjiVG strokes, memory-mapped), assets/origin.tsv (자원 and 외우기 for the
 * 상용한자, memory-mapped) and assets/radicals.tsv (the radical names, small, read once). Loaded off the main thread.
 */
object PartsStore {
    @Volatile
    private var parts: SortedTsv? = null
    @Volatile
    private var origins: SortedTsv? = null
    @Volatile
    private var radicals: List<Radical> = emptyList()

    /** Blocking (memory maps and a small file): call from a background thread. */
    fun load(ctx: Context) {
        if (parts != null) return
        synchronized(this) {
            if (parts != null) return
            radicals = runCatching {
                Radical.parseAll(ctx.assets.open("radicals.tsv").use { it.readBytes().toString(Charsets.UTF_8) })
            }.getOrDefault(emptyList())
            origins = runCatching { SortedTsv(mapAsset(ctx, "origin.tsv")) }.getOrNull()
            parts = SortedTsv(mapAsset(ctx, "parts.tsv"))
        }
    }

    fun parts(ch: String): Parts? = parts?.find(ch)?.let { Parts.parse(ch + "\t" + it) }

    fun origin(ch: String): Origin? = origins?.find(ch)?.let { Origin.parse(it) }

    /** The plain meaning of a part's shape when it is a radical's (辶 → 가다·길), for the parts line. */
    fun meaningOf(form: String): String? = Radical.meaningOf(radicals, form)

    /** The name line for this character's radical (氵 left → さんずい 삼수변). */
    fun radical(p: Parts): Radical? = if (p.radical == 0) null else Radical.find(radicals, p.radical, p.form, p.position)
}
