package com.ggumtak.hanja

/**
 * The light formatting the AI answers are asked to use (KanjiText prompts), turned into display text plus style
 * ranges; pure, so the JVM tests cover it, and cheap enough to run on every streamed update.
 *
 * - "【뜻】 …" (or a Markdown "# …" heading): a section label, in the accent colour.
 * - "- …" / "• …" / "・…" (a space after the 가운뎃점 or not): a bullet with a hanging indent.
 * - "① …" to "⑩ …": a sub-heading (one kanji of the word in 【한자별 가지치기】), in bold.
 * - "💡 연상: …": the memory line, its head in the accent colour and the line indented like a bullet.
 * - "→ …": a translation line, in the secondary colour.
 * - "= …": the line that adds up a word's kanji (【글자 풀이】), its "=" in the accent colour and the line indented
 *   like a bullet so it reads as the end of the list above it.
 * - "**…**": bold.
 * - "電気(でんき)", "食(た)べる", "食べる(たべる)": the reading made small and grey, with its Hangul pronunciation
 *   added by the app (でんき 덴키; a long vowel as "-": がっこう 갓코-), so the model never spends tokens on it and
 *   it matches the card above. Up to six hiragana of okurigana may stand between the kanji and the parenthesis.
 *   Without [parse]'s hangul the reading stays as written (でんき), for a learner who reads kana without help.
 */
object Rich {
    enum class Kind { LABEL, BOLD, BULLET, NOTE, READING, HANGUL, SUM, HEAD, TIP }

    data class Mark(val kind: Kind, val start: Int, val end: Int)

    class Doc(val text: String, val marks: List<Mark>)

    private val HEADING = Regex("""^#{1,6}\s+""")
    private val BULLET = Regex("""^\s*(?:[-*•]\s+|・\s*)""")
    private val HEAD = Regex("""^\s*[①-⑩]""")
    private val TIP = Regex("""^\s*💡\s*[^:：\n]{0,8}[:：]""")
    private val LABEL = Regex("""^\s*【[^】\n]{1,24}】""")
    private val NOTE = Regex("""^\s*→\s*""")
    private val SUM = Regex("""^\s*=\s+""")
    /** **bold**, a kanji (plus okurigana) with its kana reading, or 음독/훈독(reading) in parentheses. */
    private val INLINE = Regex(
        """\*\*(.+?)\*\*|([㐀-䶿一-鿿豈-﫿\x{20000}-\x{3134F}々][ぁ-ゖ]{0,6})[(（]([ぁ-ゖァ-ヺー]{1,20})[)）]|(음독|훈독)[(（]([ぁ-ゖァ-ヺー・]{1,20})[)）]"""
    )

    fun parse(src: String, hangul: Boolean = true): Doc {
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
                inline(line.substring(heading.range.last + 1), out, marks, hangul)
                marks.add(Mark(Kind.LABEL, start, out.length))
                continue
            }
            if (HEAD.containsMatchIn(line)) {
                inline(line.trimStart(), out, marks, hangul)
                marks.add(Mark(Kind.HEAD, start, out.length))
                continue
            }
            val bullet = BULLET.find(line)
            if (bullet != null) {
                out.append("• ")
                line = line.substring(bullet.range.last + 1)
            }
            val tip = TIP.find(line)
            if (tip != null) {
                val tipStart = out.length
                out.append(tip.value.trim())
                marks.add(Mark(Kind.TIP, tipStart, out.length))
                inline(line.substring(tip.range.last + 1), out, marks, hangul)
                marks.add(Mark(Kind.BULLET, start, out.length))
                continue
            }
            val note = if (bullet == null) NOTE.find(line) else null
            if (note != null) {
                out.append("→ ")
                line = line.substring(note.range.last + 1)
            }
            val sum = if (bullet == null && note == null) SUM.find(line) else null
            if (sum != null) {
                out.append("= ")
                marks.add(Mark(Kind.SUM, start, start + 1))
                line = line.substring(sum.range.last + 1)
            }
            val label = LABEL.find(line)
            if (label != null) {
                val labelText = label.value.trimStart()
                out.append(labelText)
                marks.add(Mark(Kind.LABEL, out.length - labelText.length, out.length))
                line = line.substring(label.range.last + 1)
            }
            inline(line, out, marks, hangul)
            if (bullet != null || sum != null) marks.add(Mark(Kind.BULLET, start, out.length))
            if (note != null) marks.add(Mark(Kind.NOTE, start, out.length))
        }
        return Doc(out.toString(), marks)
    }

    private fun inline(s: String, out: StringBuilder, marks: MutableList<Mark>, hangul: Boolean) {
        var last = 0
        for (m in INLINE.findAll(s)) {
            out.append(s, last, m.range.first)
            val bold = m.groups[1]
            val readingLabel = m.groups[4]
            if (bold != null) {
                val st = out.length
                inline(bold.value, out, marks, hangul)
                marks.add(Mark(Kind.BOLD, st, out.length))
            } else if (readingLabel != null) {
                val kana = m.groupValues[5]
                if (!hangul) {
                    out.append(m.value)
                } else {
                    out.append(readingLabel.value)
                    val st = out.length
                    out.append('(').append(kana).append(' ')
                    val hs = out.length
                    out.append(kana.split('・').joinToString("·") { KanaHangul.convert(it) })
                    val he = out.length
                    out.append(')')
                    marks.add(Mark(Kind.READING, st, out.length))
                    marks.add(Mark(Kind.HANGUL, hs, he))
                }
            } else {
                val headWord = m.groupValues[2]
                out.append(headWord)
                val kana = m.groupValues[3]
                val st = out.length
                if (!hangul) {
                    out.append('(').append(kana).append(')')
                    marks.add(Mark(Kind.READING, st, out.length))
                    last = m.range.last + 1
                    continue
                }
                out.append('(').append(kana).append(' ')
                val hs = out.length
                // 思う(おもう): the okurigana after the kanji is a stretch of its own (오모우, not 오모-).
                val tail = headWord.substring(Character.charCount(headWord.codePointAt(0)))
                out.append(if (tail.isNotEmpty() && kana.length > tail.length && kana.endsWith(tail)) {
                    KanaHangul.convertParts(listOf(kana.dropLast(tail.length), tail))
                } else KanaHangul.convert(kana))
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
