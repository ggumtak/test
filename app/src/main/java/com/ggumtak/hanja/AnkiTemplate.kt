package com.ggumtak.hanja

/**
 * The 한자 바로보기 block (now the loader, assets/anki-loader.html) inside Anki card templates: found, replaced or removed whatever
 * version it is, so the app can keep every note type on the current code without the user pasting anything.
 */
object AnkiTemplate {
    /** Every version of the block starts with this comment and ends at its first closing script tag. */
    const val MARK = "<!-- 한자 바로보기"
    private const val END = "</script>"

    data class Template(val ord: Int, val name: String, val front: String, val back: String)

    data class NoteType(val id: Long, val name: String, val templates: List<Template>) {
        val installed: Boolean get() = templates.any { has(it.front) || has(it.back) }
        fun current(code: String): Boolean = templates.all { isCurrent(it.front, code) && isCurrent(it.back, code) }
    }

    /** New text for one card type; null leaves that side as it is. */
    data class Change(val typeId: Long, val ord: Int, val front: String?, val back: String?)

    /** [broken]: names of note types left untouched because their code could not be told apart safely. */
    /** A card type whose template the app cannot write (too large) and that does not have the loader yet. */
    data class Manual(val typeId: Long, val typeName: String, val cardName: String)

    /** [manual]: card types the user must paste the loader into by hand, once. */
    class Plan(val changes: List<Change>, val broken: List<String>, val manual: List<Manual> = emptyList())

    /**
     * AnkiDroid takes at most about 1MB per call from another app, and a template travels as UTF-16 (2 bytes a
     * character): a side longer than this is never sent (it failed with "응답 -1" for the JLPT MAX 덱's 715,000+).
     */
    const val WRITE_LIMIT = 480_000

    /** A side the app cannot write but the user already pasted the loader into: done, whatever else is in it. */
    private fun pasted(template: String, code: String) = template.length > WRITE_LIMIT && template.contains(code.trim())

    fun has(template: String): Boolean = template.contains(MARK)

    fun isCurrent(template: String, code: String): Boolean =
        template.indexOf(MARK) == template.lastIndexOf(MARK) && template.contains(code.trim())

    /** [template] without any 한자 바로보기 block; null when a block has no end (cut off while pasting). */
    fun strip(template: String): String? {
        val out = StringBuilder()
        var from = 0
        while (true) {
            val start = template.indexOf(MARK, from)
            if (start < 0) {
                out.append(template, from, template.length)
                return out.toString()
            }
            val end = template.indexOf(END, start)
            if (end < 0) return null
            out.append(template, from, start)
            while (out.isNotEmpty() && out.last().isWhitespace()) out.setLength(out.length - 1)
            from = end + END.length
            while (from < template.length && template[from].isWhitespace()) from++
            if (out.isNotEmpty() && from < template.length) out.append('\n')
        }
    }

    /** A pasted block without its comment, or one cut off: never guessed at, the user checks it by hand. */
    fun broken(template: String): Boolean = strip(template)?.contains("hjtap") != false

    /** The template with exactly one block, the current one, at the end. */
    fun install(template: String, code: String): String {
        val base = strip(template)!!.trimEnd()
        return (if (base.isEmpty()) "" else base + "\n\n") + code.trim() + "\n"
    }

    private fun next(template: String, want: Boolean, code: String): String? = when {
        want && isCurrent(template, code) -> null
        want -> install(template, code)
        has(template) -> strip(template)!!.trimEnd()
        else -> null
    }

    /** Front and back of every card type of the note types in [wanted] get the current code; the others lose it. */
    fun plan(types: List<NoteType>, wanted: Set<Long>, code: String): Plan {
        val changes = mutableListOf<Change>()
        val broken = mutableListOf<String>()
        val manual = mutableListOf<Manual>()
        for (t in types) {
            val want = t.id in wanted
            if (!want && !t.installed) continue
            if (t.templates.any { broken(it.front) || broken(it.back) }) {
                broken += t.name
                continue
            }
            for (tmpl in t.templates) {
                fun side(text: String): String? {
                    if (want && pasted(text, code)) return null
                    val next = next(text, want, code) ?: return null
                    return if (next.length > WRITE_LIMIT) null else next
                }
                val front = side(tmpl.front)
                val back = side(tmpl.back)
                if (front != null || back != null) changes += Change(t.id, tmpl.ord, front, back)
                val stuck = listOf(tmpl.front, tmpl.back).any { text ->
                    want && !pasted(text, code) && !isCurrent(text, code) && next(text, want, code)?.let { it.length > WRITE_LIMIT } == true
                }
                if (stuck) manual += Manual(t.id, t.name, tmpl.name)
            }
        }
        return Plan(changes, broken, manual)
    }

    /** Whether [actual], read back from Anki, is what a write of [expected] should have left there. */
    private fun landed(expected: String?, actual: String, code: String): Boolean = when {
        expected == null -> true
        has(expected) -> isCurrent(actual, code)
        else -> !has(actual)
    }

    /**
     * The changes of [plan] that are not in [after], the note types read again once the plan was written:
     * AnkiDroid can drop a write without an error, so "written" is only said of what is really there.
     */
    fun missed(plan: Plan, after: List<NoteType>, code: String): List<Change> = plan.changes.filter { c ->
        val t = after.firstOrNull { it.id == c.typeId }?.templates?.firstOrNull { it.ord == c.ord }
        t == null || !landed(c.front, t.front, code) || !landed(c.back, t.back, code)
    }

    /**
     * The note types to keep on the current code: those that have the block, and those the user chose before
     * ([chosen]) even when a deck update replaced their templates and took the block with it.
     */
    fun keep(types: List<NoteType>, chosen: Set<Long>): Set<Long> =
        types.filter { it.installed || it.id in chosen }.map { it.id }.toSet()

    /** Brings [keep]'s note types to the current code; touches nothing else. */
    fun refresh(types: List<NoteType>, code: String, chosen: Set<Long> = emptySet()): Plan =
        plan(types, keep(types, chosen), code)
}
