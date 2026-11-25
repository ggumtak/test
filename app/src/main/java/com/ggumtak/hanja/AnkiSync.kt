package com.ggumtak.hanja

import android.content.Context

/**
 * Keeps the Anki card code current from anywhere in the app, not only the first screen: the note types that have
 * the code, or that the user chose ([Prefs.ankiTapTypes]), get the loader (AnkiCode), and Anki's media folder the
 * current code file. Blocking (AnkiDroid IPC): background threads only.
 */
object AnkiSync {
    private const val GAP_MS = 30 * 60_000L

    @Volatile
    private var lastRun = 0L

    /** What goes into templates: the loader. */
    fun code(ctx: Context): String = AnkiCode.loader(ctx)

    /**
     * The current code file in Anki's media folder; null when it is there under its own name, else what went wrong.
     * Templates the app cannot write (too large) run it as soon as the user has pasted the loader into them.
     */
    fun ensureCode(ctx: Context): String? {
        val stored = runCatching { AnkiBridge.addCode(ctx) }
        val want = AnkiCode.MEDIA_NAME + ".js"
        return when {
            stored.getOrNull() == want -> null
            stored.isFailure -> "Anki 미디어에 코드 파일을 넣지 못했습니다: " + stored.exceptionOrNull()!!.javaClass.simpleName
            else -> "Anki 미디어에 코드 파일이 다른 이름(${stored.getOrNull()})으로 들어갔습니다"
        }
    }

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
            // A loader without its code file would leave the cards without taps: no template change then.
            if (keep.isNotEmpty() && ensureCode(ctx) != null) return null
            val plan = AnkiTemplate.plan(types, keep, code(ctx))
            plan.changes.forEach { AnkiBridge.write(cr, it) }
            plan
        }.getOrNull()
    }
}
