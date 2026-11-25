package com.ggumtak.hanja

import android.content.Context

/**
 * Keeps the Anki card code current from anywhere in the app, not only the first screen: the note types that have
 * the code, or that the user chose ([Prefs.ankiTapTypes]), get the current code in their CSS (AnkiCss) and the
 * loader in their templates (AnkiCode). AnkiDroid only: myAmgi runs the card code by itself
 * ([AnkiTarget.needsTemplateCode]), so nothing happens while myAmgi is the chosen app.
 * Blocking (AnkiDroid IPC): background threads only.
 */
object AnkiSync {
    private const val GAP_MS = 30 * 60_000L

    @Volatile
    private var lastRun = 0L

    /** What goes into templates: the loader. */
    fun code(ctx: Context): String = AnkiCode.loader(ctx)

    /** Note types in [keep] whose CSS could not be brought to the current code, with what AnkiDroid answered. */
    class CssResult(val failed: Map<Long, String>)

    /** The current code into the CSS of every note type in [keep] that does not have it yet. */
    fun writeCss(ctx: Context, types: List<AnkiTemplate.NoteType>, keep: Set<Long>): CssResult {
        val cr = AnkiBridge.open(ctx)
        val block = AnkiCode.cssBlock(ctx)
        val failed = HashMap<Long, String>()
        for (t in types) {
            if (t.id !in keep) continue
            val css = AnkiBridge.css(cr, t.id)
            if (css == null) {
                failed[t.id] = "CSS 읽기 실패"
                continue
            }
            if (AnkiCss.isCurrent(css, block)) continue
            AnkiBridge.writeCss(cr, t.id, AnkiCss.install(css, block))?.let { failed[t.id] = it }
        }
        return CssResult(failed)
    }

    /**
     * The template changes of [plan] that are safe to write: a loader without the code in its note type's CSS
     * would leave the cards without taps, so those note types keep the code they have.
     */
    fun safe(plan: AnkiTemplate.Plan, css: CssResult): AnkiTemplate.Plan =
        AnkiTemplate.Plan(plan.changes.filter { it.typeId !in css.failed }, plan.broken, plan.manual)

    /**
     * Brings the code up to date; the plan that was carried out, or null when there was nothing to read or
     * AnkiDroid did not answer. At most once every half hour unless [force].
     */
    fun refresh(ctx: Context, force: Boolean = false): AnkiTemplate.Plan? {
        val now = System.currentTimeMillis()
        if (!force && now - lastRun in 0 until GAP_MS) return null
        lastRun = now
        if (AnkiBridge.target(ctx)?.needsTemplateCode != true || !AnkiBridge.granted(ctx)) return null
        return runCatching {
            val cr = AnkiBridge.open(ctx)
            val types = AnkiBridge.noteTypes(cr)
            val keep = AnkiTemplate.keep(types, Prefs.ankiTapTypes(ctx))
            Prefs.setAnkiTapTypes(ctx, keep)
            val css = writeCss(ctx, types, keep)
            val plan = safe(AnkiTemplate.plan(types, keep, code(ctx)), css)
            plan.changes.forEach { AnkiBridge.write(cr, it) }
            plan
        }.getOrNull()
    }
}
