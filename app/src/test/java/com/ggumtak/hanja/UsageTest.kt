package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsageTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    private val usage: Map<String, Usage> by lazy {
        asset("usage.tsv").readLines().filter { it.isNotEmpty() }.associate { line ->
            val ch = line.substringBefore('\t')
            ch to (Usage.parse(line.substringAfter('\t')) ?: error("no reading: $line"))
        }
    }

    private val kanji: Map<String, Kanji> by lazy {
        asset("kanji.tsv").readLines().mapNotNull { Kanji.parse(it) }.associateBy { it.ch }
    }

    @Test
    fun parsesReadingsWithTheirWordsAndMarks() {
        val u = Usage.parse("セイ|学生:がくせい:학생|先生:せんせい:선생님;（ショウ）|一生:いっしょう:평생;キン\t" +
            "い.きる|生きる:いきる:살다;*た.つ|経つ:たつ:(시간이) 지나다;なま|生:なま:")!!
        assertEquals(listOf("セイ", "ショウ", "キン"), u.on.map { it.reading })
        assertEquals(listOf(false, true, false), u.on.map { it.limited })
        assertEquals(Usage.Word("学生", "がくせい", "학생"), u.on[0].words[0])
        assertEquals(2, u.on[0].words.size)
        assertTrue(u.on[2].words.isEmpty())
        assertEquals(listOf("い.きる", "た.つ", "なま"), u.kun.map { it.reading })
        assertEquals("い", u.kun[0].stem)
        assertEquals("きる", u.kun[0].okurigana)
        assertEquals("いきる", u.kun[0].joined)
        assertTrue(u.kun[1].outside)
        assertFalse(u.kun[0].outside)
        // A word without a meaning still shows.
        assertEquals(Usage.Word("生", "なま", ""), u.kun[2].words[0])
    }

    @Test
    fun aLineWithoutReadingsIsNoUsage() {
        assertNull(Usage.parse("\t"))
        assertNull(Usage.parse(""))
        // Only kun readings (込, 畑).
        assertNotNull(Usage.parse("\tこ.む|込む:こむ:붐비다"))
    }

    @Test
    fun aKunReadingKnowsItsOwnWord() {
        val live = Usage.Reading("い.きる", limited = false, outside = false, words = emptyList())
        assertTrue(live.isOwn("生", Usage.Word("生きる", "いきる", "살다")))
        assertFalse(live.isOwn("生", Usage.Word("生き物", "いきもの", "생물")))
        val hand = Usage.Reading("て", limited = false, outside = false, words = emptyList())
        assertTrue(hand.isOwn("手", Usage.Word("手", "て", "손")))
        assertFalse(hand.isOwn("手", Usage.Word("手紙", "てがみ", "편지")))
    }

    @Test
    fun thePlanShowsReadingsWithWordsFirstAndKeepsTheRestBehind() {
        val u = Usage.parse("ア|亜:あ:가;イ;ウ|右:う:나;エ|絵:え:다;オ|尾:お:라\t" +
            "か.く|書く:かく:쓰다;き;く.る|来る:くる:오다;け|毛:け:털;こ|子:こ:아이;さ.す|刺す:さす:찌르다")!!
        val p = u.plan("ア・イ・ウ・エ・オ・カン", "か.く・き・く.る・け・こ・さ.す・さ.し・し.ぬ・-す.る")
        // Three on readings that have a word; the wordless イ waits with the rest.
        assertEquals(listOf("ア", "ウ", "エ"), p.on.map { it.reading })
        assertEquals(listOf("イ", "オ"), p.moreOn.map { it.reading })
        assertEquals(listOf("か.く", "く.る", "け", "こ"), p.kun.map { it.reading })
        assertEquals(listOf("き", "さ.す"), p.moreKun.map { it.reading })
        // The dictionary's readings that the line does not have at all; an affix form (-す.る) is no reading to show.
        assertEquals(listOf("カン"), p.otherOn)
        assertEquals(listOf(KanjiText.Kun("さ", "し"), KanjiText.Kun("し", "ぬ")), p.otherKun)
        assertEquals(2 + 2 + 1 + 2, p.hidden)
    }

    @Test
    fun aKanjiWhoseReadingsHaveNoWordStillShowsItsFirst() {
        val p = Usage.parse("キン;コン\t")!!.plan("キン・コン", "")
        assertEquals(listOf("キン"), p.on.map { it.reading })
        assertEquals(listOf("コン"), p.moreOn.map { it.reading })
        assertEquals(0, p.kun.size)
    }

    @Test
    fun oneWordIsOneKunReadingHoweverKanjidicSplitsIt() {
        // 生: う.まれる and うま.れる are the same word.
        val kun = "い.きる・う.まれる・うま.れる・う.む"
        assertEquals(4, KanjiText.kunReadings(kun).size)
        assertEquals(listOf("いきる", "うまれる", "うむ"),
            KanjiText.kunReadings(kun, distinctWords = true).map { it.stem + it.okurigana })
    }

    @Test
    fun everyJoyoKanjiHasItsReadings() {
        val joyo = kanji.values.filter { it.grade in 1..6 || it.grade == 8 }
        assertEquals(2136, joyo.size)
        val missing = joyo.filter { it.ch !in usage }.map { it.ch }
        assertEquals("상용한자 without a usage line: $missing", emptyList<String>(), missing)
        // Four have a second shape in everyday use (剥 剝, 叱 𠮟, 塡 填, 頰 頬): both shapes have a line, each with
        // its words spelt its own way.
        assertEquals(2140, usage.size)
        for ((a, b) in listOf("剥" to "剝", "叱" to "𠮟", "塡" to "填", "頰" to "頬")) {
            val mine = usage.getValue(a)
            val other = usage.getValue(b)
            assertEquals(mine.kun.map { it.reading }, other.kun.map { it.reading })
            assertTrue((other.on + other.kun).all { r -> r.words.all { b in it.form && a !in it.form } })
        }
    }

    @Test
    fun theDataIsWellFormed() {
        val kana = Regex("^[ぁ-ゖー]+$")
        val meaning = Regex("^[가-힣0-9 ,()~·]+$")
        val problems = ArrayList<String>()
        for ((ch, u) in usage) {
            for (r in u.on) if (!r.reading.matches(Regex("^[ァ-ヺー]+$"))) problems.add("$ch on ${r.reading}")
            for (r in u.kun) if (!r.reading.matches(Regex("^[ぁ-ゖ]+(\\.[ぁ-ゖ]+)?$"))) problems.add("$ch kun ${r.reading}")
            if ((u.on + u.kun).map { it.reading }.toSet().size != u.on.size + u.kun.size) problems.add("$ch repeats a reading")
            if (u.kun.map { it.joined }.toSet().size != u.kun.size) problems.add("$ch has one kun word twice")
            for (r in u.on + u.kun) for (w in r.words) {
                if (!w.kana.matches(kana)) problems.add("$ch ${w.form} kana ${w.kana}")
                if (ch !in w.form) problems.add("$ch is not in ${w.form}")
                if (w.meaning.isEmpty()) problems.add("$ch ${w.form} has no meaning")
                else if (!w.meaning.matches(meaning) || w.meaning.length > 20) problems.add("$ch ${w.form}: ${w.meaning}")
            }
        }
        assertEquals(problems.take(30).joinToString("\n"), 0, problems.size)
    }

    @Test
    fun theReadingsALearnerMeetsComeFirst() {
        // 食: た.べる before く.う and く.らう (KANJIDIC's order put it third, after く.らう).
        assertEquals(listOf("た.べる", "く.う", "く.らう"), usage.getValue("食").kun.map { it.reading })
        // 生: one うまれる, and the table's なま, は.える, う.む are there (they used to be cut off after five).
        val live = usage.getValue("生").kun.map { it.reading }
        assertEquals(1, live.count { it.replace(".", "") == "うまれる" })
        assertTrue(live.containsAll(listOf("い.きる", "う.まれる", "なま", "は.える", "う.む")))
        assertEquals("い.きる", live[0])
        // 下: all ten of the table's kun readings.
        assertTrue(usage.getValue("下").kun.map { it.reading }.containsAll(listOf("した", "くだ.る", "お.りる", "さ.げる")))
        // 明: 明るい (N5) first.
        assertEquals("あか.るい", usage.getValue("明").kun[0].reading)
        // 行: the table's order among equals (い.く before ゆ.く).
        assertEquals(listOf("い.く", "ゆ.く", "おこな.う"), usage.getValue("行").kun.map { it.reading })
        // 風: the main reading before the limited one, which keeps its everyday word.
        val wind = usage.getValue("風").on
        assertEquals(listOf("フウ", "フ"), wind.map { it.reading })
        assertTrue(wind[1].limited)
        assertEquals("風呂", wind[1].words[0].form)
    }

    @Test
    fun readingsOutsideTheTableAreFewAndMarked() {
        val outside = usage.flatMap { (ch, u) -> (u.on + u.kun).filter { it.outside }.map { ch + it.reading } }
        assertTrue("outside the table: $outside", outside.size in 1..12)
        assertTrue("経た.つ" in outside)
        // The dictionary's rare readings are not in the line at all (食: は.む; 楽: ゴウ, この.む).
        assertFalse(usage.getValue("食").kun.any { it.reading == "は.む" })
        assertFalse(usage.getValue("楽").on.any { it.reading == "ゴウ" })
        val p = usage.getValue("楽").plan(kanji.getValue("楽").on, kanji.getValue("楽").kun)
        assertEquals(listOf("ゴウ"), p.otherOn)
        assertEquals(listOf(KanjiText.Kun("この", "む")), p.otherKun)
    }

    @Test
    fun aVerbAndItsPartnerKeepTheirOwnMeanings() {
        fun meaning(ch: String, reading: String) = usage.getValue(ch).kun.first { it.reading == reading }.words[0].meaning
        assertEquals("열다", meaning("開", "あ.ける"))
        assertEquals("열리다", meaning("開", "あ.く"))
        assertEquals("시작하다", meaning("始", "はじ.める"))
        assertEquals("시작되다", meaning("始", "はじ.まる"))
        assertTrue(meaning("食", "た.べる").startsWith("먹다"))
    }

    @Test
    fun mostReadingsCarryAWord() {
        val all = usage.values.flatMap { it.on + it.kun }
        val without = all.count { it.words.isEmpty() }
        assertTrue("$without of ${all.size} readings have no word", without < all.size * 0.05)
        // Every kanji shows at least one reading at once.
        for ((ch, u) in usage) {
            val p = u.plan(kanji.getValue(ch).on, kanji.getValue(ch).kun)
            assertTrue(ch, p.on.isNotEmpty() || p.kun.isNotEmpty())
        }
    }
}
