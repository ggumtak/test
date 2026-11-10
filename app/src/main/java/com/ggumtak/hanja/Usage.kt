package com.ggumtak.hanja

/**
 * One line of assets/usage.tsv (tools/build_usage_data.py), for the 상용한자: the readings worth learning, the ones a
 * learner meets soonest first, each with one or two common words that use it (生 セイ: 学生 がくせい 학생). The
 * readings are the 상용한자표's own, plus the few outside it that a well-known word is written with (経: た.つ).
 * KANJIDIC lists many more (食: は.む), which the card keeps out of the way ([plan]).
 */
data class Usage(val on: List<Reading>, val kun: List<Reading>) {
    /** A word that uses the reading: as written, its reading in hiragana and a short Korean meaning (may be empty). */
    data class Word(val form: String, val kana: String, val meaning: String)

    /**
     * A reading: katakana for on, "stem.okurigana" for kun (い.きる). [limited]: the table keeps it to a few words
     * (風: フ of 風呂). [outside]: not in the table, but a common word is written with it.
     */
    data class Reading(val reading: String, val limited: Boolean, val outside: Boolean, val words: List<Word>) {
        /** The part the kanji stands for (い of い.きる; all of an on reading). */
        val stem: String get() = reading.substringBefore('.')

        /** The kana written after the kanji (きる), or empty. */
        val okurigana: String get() = reading.substringAfter('.', "")

        /** The reading as one word (いきる). */
        val joined: String get() = stem + okurigana

        /**
         * Whether [word] is this kun reading's own word for the kanji [ch] (生きる for い.きる, 手 for て): the card
         * then shows only its meaning after the reading, since the reading already spells the word.
         */
        fun isOwn(ch: String, word: Word): Boolean = word.form == ch + okurigana && word.kana == joined
    }

    /**
     * What the card shows of a kanji's readings: [on] and [kun] at once; [moreOn] and [moreKun] (the rest of this
     * line) and [otherOn] and [otherKun] (KANJIDIC's readings that are not here at all: rare ones and other forms of
     * the same words) only when asked for.
     */
    class Plan(
        val on: List<Reading>,
        val kun: List<Reading>,
        val moreOn: List<Reading>,
        val moreKun: List<Reading>,
        val otherOn: List<String>,
        val otherKun: List<KanjiText.Kun>,
    ) {
        /** How many readings wait behind "더 보기". */
        val hidden: Int get() = moreOn.size + moreKun.size + otherOn.size + otherKun.size
    }

    /**
     * Splits the readings for the card. Shown at once: the first [ON_SHOWN] on and [KUN_SHOWN] kun readings that
     * have a word (a reading no common word uses is not worth the first look; without any, the first reading).
     * [dictOn] and [dictKun] are KANJIDIC's lists (Kanji.on, Kanji.kun).
     */
    fun plan(dictOn: String, dictKun: String): Plan {
        fun split(all: List<Reading>, limit: Int): Pair<List<Reading>, List<Reading>> {
            val shown = all.filter { it.words.isNotEmpty() }.take(limit).ifEmpty { all.take(1) }
            return shown to all.filter { it !in shown }
        }
        val (on, moreOn) = split(this.on, ON_SHOWN)
        val (kun, moreKun) = split(this.kun, KUN_SHOWN)
        val knownOn = this.on.map { it.reading }.toSet()
        val knownKun = this.kun.map { it.joined }.toSet()
        val otherOn = KanjiText.onReadings(dictOn, OTHERS).filter { it !in knownOn }
        val otherKun = KanjiText.kunReadings(dictKun, OTHERS * 3, distinctWords = true)
            .filter { it.stem + it.okurigana !in knownKun }.take(OTHERS)
        return Plan(on, kun, moreOn, moreKun, otherOn, otherKun)
    }

    companion object {
        const val ON_SHOWN = 3
        const val KUN_SHOWN = 4
        /** At most this many of KANJIDIC's other on readings, and as many kun, behind "더 보기". */
        const val OTHERS = 8

        /** The value of a usage.tsv line (after the character and its tab): "on<TAB>kun"; null when it has no reading. */
        fun parse(value: String): Usage? {
            val f = value.split('\t')
            val on = readings(f[0])
            val kun = readings(f.getOrElse(1) { "" })
            return if (on.isEmpty() && kun.isEmpty()) null else Usage(on, kun)
        }

        /** "セイ|学生:がくせい:학생|先生:せんせい:선생님;（ショウ）|…;*ズ|…" */
        private fun readings(field: String): List<Reading> = field.split(';').mapNotNull { item ->
            val cells = item.split('|')
            val head = cells[0].trim()
            val outside = head.startsWith("*")
            val limited = head.startsWith("（") && head.endsWith("）")
            val reading = head.removePrefix("*").removePrefix("（").removeSuffix("）")
            if (reading.isEmpty()) return@mapNotNull null
            val words = cells.drop(1).mapNotNull { cell ->
                val w = cell.split(':')
                if (w.size < 2 || w[0].isEmpty() || w[1].isEmpty()) null
                else Word(w[0], w[1], w.getOrElse(2) { "" }.trim())
            }
            Reading(reading, limited, outside, words)
        }
    }
}
