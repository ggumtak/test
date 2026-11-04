package com.ggumtak.hanja

import android.content.Context

/**
 * Keeps the Anki card code current from anywhere in the app, not only the first screen: the note types that have
 * the code, or that the user chose ([Prefs.ankiTapTypes]), get the current assets/anki-tap.html. Blocking
 * (AnkiDroid IPC): background threads only.
 */
object AnkiSync {
    private const val GAP_MS = 30 * 60_000L

    @Volatile
    private var lastRun = 0L

    fun code(ctx: Context): String = ctx.assets.open("anki-tap.html").use { it.readBytes().toString(Charsets.UTF_8) }

    /**
     * Brings the code up to date; the plan that was carried out, or null when there was nothing to read or
     * AnkiDroid did not answer. At most once every half hour unless [force].
     */
    fun refresh(ctx: Context, force: Boolean = false): AnkiTemplate.Plan? {
        val now = System.currentTimeMillis()
        if (!force && now - lastRun in 0 until GAP_MS) return null
        lastRun = now
        if (!AnkiBridge.installed(ctx) || !AnkiBridge.granted(ctx)) return null
        return runCatching {
            val cr = ctx.contentResolver
            val types = AnkiBridge.noteTypes(cr)
            val keep = AnkiTemplate.keep(types, Prefs.ankiTapTypes(ctx))
            Prefs.setAnkiTapTypes(ctx, keep)
            val plan = AnkiTemplate.plan(types, keep, code(ctx))
            plan.changes.forEach { AnkiBridge.write(cr, it) }
            plan
        }.getOrNull()
    }
}
