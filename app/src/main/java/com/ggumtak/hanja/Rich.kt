package com.ggumtak.hanja

/**
 * The light formatting the AI answers are asked to use (KanjiText prompts), turned into display text plus style
 * ranges; pure, so the JVM tests cover it, and cheap enough to run on every streamed update.
 *
 * - "【뜻】 …" (or a Markdown "# …" heading): a section label, in the accent colour.
 * - "- …" / "• …": a bullet with a hanging indent.
 * - "→ …": a translation line, in the secondary colour.
 * - "**…**": bold.
 * - "電気(でんき)", "食(た)べる", "食べる(たべる)": the reading made small and grey, with its Hangul pronunciation
 *   added by the app (でんき 덴키), so the model never spends tokens on it and it matches the card above. Up to six
 *   hiragana of okurigana may stand between the kanji and the parenthesis.
 */
object Rich {
    enum class Kind { LABEL, BOLD, BULLET, NOTE, READING, HANGUL }

    data class Mark(val kind: Kind, val start: Int, val end: Int)

    class Doc(val text: String, val marks: List<Mark>)

    private val HEADING = Regex("""^#{1,6}\s+""")
    private val BULLET = Regex("""^\s*[-*•・]\s+""")
    private val LABEL = Regex("""^【[^】\n]{1,12}】""")
    private val NOTE = Regex("""^\s*→\s*""")
    /** **bold**, or a kanji (plus okurigana) followed by its kana reading in half- or full-width parentheses. */
    private val INLINE = Regex("""\*\*(.+?)\*\*|([㐀-䶿一-鿿豈-﫿々][ぁ-ゖ]{0,6})[(（]([ぁ-ゖァ-ヺー]{1,20})[)）]""")

    fun parse(src: String): Doc {
        val out = StringBuilder(src.length + 32)
        val marks = ArrayList<Mark>()
        // At most one empty line between blocks.
        val lines = src.replace(Regex("""\n{3,}"""), "\n\n").split('\n')
        for ((i, raw) in lines.withIndex()) {
            if (i > 0) out.append('\n')
            val start = out.length
            var line = raw
            val heading = HEADING.find(line)
            if (heading != null) {
                inline(line.substring(heading.range.last + 1), out, marks)
                marks.add(Mark(Kind.LABEL, start, out.length))
                continue
            }
            val bullet = BULLET.find(line)
            if (bullet != null) {
                out.append("• ")
                line = line.substring(bullet.range.last + 1)
            }
            val note = if (bullet == null) NOTE.find(line) else null
            if (note != null) {
                out.append("→ ")
                line = line.substring(note.range.last + 1)
            }
            val label = LABEL.find(line)
            if (label != null) {
                out.append(label.value)
                marks.add(Mark(Kind.LABEL, out.length - label.value.length, out.length))
                line = line.substring(label.range.last + 1)
            }
            inline(line, out, marks)
            if (bullet != null) marks.add(Mark(Kind.BULLET, start, out.length))
            if (note != null) marks.add(Mark(Kind.NOTE, start, out.length))
        }
        return Doc(out.toString(), marks)
    }

    private fun inline(s: String, out: StringBuilder, marks: MutableList<Mark>) {
        var last = 0
        for (m in INLINE.findAll(s)) {
            out.append(s, last, m.range.first)
            val bold = m.groups[1]
            if (bold != null) {
                val st = out.length
                inline(bold.value, out, marks)
                marks.add(Mark(Kind.BOLD, st, out.length))
            } else {
                out.append(m.groupValues[2])
                val kana = m.groupValues[3]
                val st = out.length
                out.append('(').append(kana).append(' ')
                val hs = out.length
                out.append(KanaHangul.convert(kana))
                val he = out.length
                out.append(')')
                marks.add(Mark(Kind.READING, st, out.length))
                marks.add(Mark(Kind.HANGUL, hs, he))
            }
            last = m.range.last + 1
        }
        out.append(s, last, s.length)
    }
}
