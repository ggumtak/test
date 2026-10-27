package com.ggumtak.hanja

/**
 * One line of assets/kanji.tsv (tools/build_kanji_data.py): the Korean 훈음 ([hun], taken from the traditional
 * form [variant] when the Japanese shape has none of its own), the Japanese readings and KANJIDIC's stroke
 * count, school grade and English meanings.
 */
data class Kanji(
    val ch: String,
    val hun: String,
    val variant: String,
    val on: String,
    val kun: String,
    val strokes: Int,
    val grade: Int,
    val english: String,
) {
    /** Made in Japan (働, 込): no Korean reading exists. */
    val kokuji: Boolean get() = english.contains("(kokuji)")

    /** "초등 1학년", "상용한자", "인명용" or empty. */
    val gradeLabel: String
        get() = when (grade) {
            in 1..6 -> "초등 ${grade}학년"
            8 -> "상용한자"
            9, 10 -> "인명용"
            else -> ""
        }

    /** The English meanings without the "(kokuji)" marker. */
    val meanings: String
        get() = english.split("; ").filter { it.isNotBlank() && it != "(kokuji)" }.joinToString(", ")

    companion object {
        fun parse(line: String): Kanji? {
            val f = line.split('\t')
            if (f.size < 8 || f[0].isEmpty()) return null
            return Kanji(f[0], f[1], f[2], f[3], f[4], f[5].toIntOrNull() ?: 0, f[6].toIntOrNull() ?: 0, f[7])
        }
    }
}
