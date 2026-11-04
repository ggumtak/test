package com.ggumtak.hanja

/**
 * The 한자 바로보기 block (assets/anki-tap.html) inside Anki card templates: found, replaced or removed whatever
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
    class Plan(val changes: List<Change>, val broken: List<String>)

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
        for (t in types) {
            val want = t.id in wanted
            if (!want && !t.installed) continue
            if (t.templates.any { broken(it.front) || broken(it.back) }) {
                broken += t.name
                continue
            }
            for (tmpl in t.templates) {
                val front = next(tmpl.front, want, code)
                val back = next(tmpl.back, want, code)
                if (front != null || back != null) changes += Change(t.id, tmpl.ord, front, back)
            }
        }
        return Plan(changes, broken)
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
