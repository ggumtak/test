package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingsTest {
    private val kanji: SortedTsv by lazy {
        val f = listOf("src/main/assets/kanji.tsv", "app/src/main/assets/kanji.tsv").map(::File).first { it.exists() }
        SortedTsv(f.readBytes())
    }
    private val of: (String) -> List<String>? =
        { ch -> kanji.find(ch)?.let { Kanji.parse(ch + "\t" + it) }?.let(Readings::candidates) }

    @Test
    fun splitsRegularWords() {
        assertEquals(listOf("でん", "き"), Readings.split("電気", "でんき", of))
        assertEquals(listOf("あか", "る", "い"), Readings.split("明るい", "あかるい", of))
        assertEquals(listOf("た", "なか"), Readings.split("田中", "たなか", of))
    }

    @Test
    fun allowsSoundChanges() {
        assertEquals(listOf("がっ", "こう"), Readings.split("学校", "がっこう", of))
        assertEquals(listOf("ひと", "びと"), Readings.split("人々", "ひとびと", of))
        assertEquals(listOf("にっ", "ぽん"), Readings.split("日本", "にっぽん", of))
    }

    @Test
    fun wholeWordReadingsDoNotSplit() {
        assertNull(Readings.split("部屋", "へや", of))
        assertNull(Readings.split("今日", "きょう", of))
        assertNull(Readings.split("大人", "おとな", of))
    }

    @Test
    fun formsToLookUp() {
        assertEquals(listOf("暖かくな", "暖かく", "暖か"), Readings.lookupForms("暖", "まだ暖かくなかった"))
        assertEquals(listOf("部屋", "部屋は"), Readings.lookupForms("部屋", "昨日の部屋は、まだ"))
        // The greeting 今日は must not win over 今日 + the particle は.
        assertEquals("今日", Readings.lookupForms("今日", "今日は雨ですが、学校へ行きます。").first())
        assertEquals(listOf("雨ですが", "雨です", "雨で", "雨"), Readings.lookupForms("雨", "今日は雨ですが"))
        assertEquals(listOf("書いてい", "書いて", "書い"), Readings.lookupForms("書", "まだ書いていません"))
        assertEquals(listOf("明るく", "明る"), Readings.lookupForms("明るく", ""))
        assertEquals(listOf("暖"), Readings.lookupForms("暖", ""))
        assertEquals(listOf("電気"), Readings.lookupForms("電気", ""))
    }

    @Test
    fun findsWordsInSortedBytes() {
        val idx = SortedTsv("今日\tきょう・こんにち\n部屋\tへや\n部屋割\tへやわり\n".toByteArray())
        assertEquals("へや", idx.find("部屋"))
        assertEquals("きょう・こんにち", idx.find("今日"))
        assertNull(idx.find("部"))
        assertNull(idx.find("部屋割り"))
    }

    /** Taps on real card sentences, against the shipped data: the word found and how its kanji split. */
    @Test
    fun tapsOnCardSentences() {
        val f = listOf("src/main/assets/words.tsv", "app/src/main/assets/words.tsv").map(::File).first { it.exists() }
        val idx = SortedTsv(f.readBytes())
        fun tap(run: String, sentence: String): String {
            val (form, r) = Readings.findWord(run, sentence) { w -> idx.find(w)?.split('・').orEmpty() } ?: return "-"
            val split = Readings.split(form, r[0], of)
            return "$form ${r.joinToString("/")} ${split?.joinToString("|") ?: "whole"}"
        }
        val s1 = "今日は雨ですが、学校へ行きます。"
        assertEquals("今日 きょう/こんにち whole", tap("今日", s1))
        assertEquals("雨 あめ あめ", tap("雨", s1))
        assertEquals("学校 がっこう がっ|こう", tap("学校", s1))
        val s2 = "昨日の部屋は、まだ暖かくなかったです。"
        assertEquals("部屋 へや whole", tap("部屋", s2))
        assertEquals("昨日 きのう/さくじつ whole", tap("昨日", s2))
        assertEquals("暖か あたたか/あったか あたた|か", tap("暖", s2))
        assertEquals("電気 でんき でん|き", tap("電気", "母は電気をつけて"))
    }

    @Test
    fun readsJustTheSelection() {
        assertEquals("は", Readings.fromParts("貼", mapOf("貼" to "は")))
        assertEquals("がっこう", Readings.fromParts("学校", mapOf("学" to "がっ", "校" to "こう")))
        assertNull(Readings.fromParts("今日", emptyMap()))
        assertNull(Readings.fromParts("ある", emptyMap()))
        assertEquals("あたた", Readings.prefixReading("暖", "暖か", listOf("あたた", "か")))
        assertEquals("でんき", Readings.prefixReading("電気", "電気", listOf("でん", "き")))
        assertNull(Readings.prefixReading("気", "電気", listOf("でん", "き")))
    }

    @Test
    fun shippedWordsHaveCommonReadings() {
        val f = listOf("src/main/assets/words.tsv", "app/src/main/assets/words.tsv").map(::File).first { it.exists() }
        val idx = SortedTsv(f.readBytes())
        assertEquals("へや", idx.find("部屋"))
        assertEquals("でんき", idx.find("電気"))
        assertEquals("あたたか", idx.find("暖か")!!.split('・').first())
        assertNull(idx.find("暖かく"))
    }
    @Test
    fun ambiguousSuffixesAreMemoizedAndCandidatesAreLoadedOnce() {
        var calls = 0
        assertNull(Readings.split("一".repeat(12), "あ".repeat(48) + "い") {
            calls++
            (1..12).map { "あ".repeat(it) }
        })
        assertEquals(12, calls)
    }

    @Test
    fun lookupPrefixesDoNotSplitSurrogates() {
        assertEquals(listOf("学校𠀋", "学校"), Readings.lookupForms("学校𠀋", ""))
    }

}
