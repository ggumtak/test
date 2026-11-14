package com.ggumtak.hanja

/**
 * One line of assets/kanji.tsv (tools/build_kanji_data.py): the Korean 훈음 ([hun], taken from the traditional
 * form [variant] when the Japanese shape has none of its own), the Japanese readings and KANJIDIC's stroke
 * count, school grade and English meanings; for the 상용한자, [gloss] (what it means in Japanese, in plain modern
 * Korean, most common first), [englishKo] (the English meanings in Korean) and [hunJa] from tools/gloss.
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
    val gloss: String = "",
    val englishKo: String = "",
    /** The 훈음 most natural for the Japanese usage first (所 → 곳 소, 바 소), for the 상용한자. */
    val hunJa: String = "",
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
            return Kanji(f[0], f[1], f[2], f[3], f[4], f[5].toIntOrNull() ?: 0, f[6].toIntOrNull() ?: 0, f[7],
                f.getOrElse(8) { "" }, f.getOrElse(9) { "" }, f.getOrElse(10) { "" })
        }
    }
}

/**
 * One line of assets/extra.tsv (tools/extra): a radical or a part outside the 상용한자 that the user's Anki decks
 * teach as a card of its own (厂, 彡, 昜). The dictionaries have only raw data for these (厂: "언덕", かりがね), so
 * the 훈음 ([hun]: 기슭 엄), what the part means in plain words ([gloss]: 벼랑), the 유래, the 외우기 and the 상용한자
 * it is seen in ([used]: 原反圧) are written for them, as they are for the 상용한자.
 */
data class Extra(val hun: String, val gloss: String, val origin: String, val memo: String, val used: String) {
    /** [k] with this line's 훈음 and 뜻 where it has none of its own; a line of its own when KANJIDIC has none. */
    fun fill(ch: String, k: Kanji?): Kanji =
        if (k == null) Kanji(ch, "", "", "", "", 0, 0, "", gloss = gloss, hunJa = hun)
        else k.copy(gloss = k.gloss.ifEmpty { gloss }, hunJa = k.hunJa.ifEmpty { hun })

    fun asOrigin(): Origin? = if (origin.isEmpty()) null else Origin(origin, memo)

    companion object {
        /** "훈음<TAB>뜻<TAB>유래<TAB>외우기<TAB>쓰임"; "-" is an empty field. */
        fun parse(value: String): Extra? {
            val f = value.split('\t').map { it.trim().let { v -> if (v == "-") "" else v } }
            if (f.size < 4) return null
            return Extra(f[0], f[1], f[2], f[3], f.getOrElse(4) { "" })
        }
    }
}
