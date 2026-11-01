package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ReadingsTest {
    private val kanji: Map<String, String> by lazy {
        val f = listOf("src/main/assets/kanji.tsv", "app/src/main/assets/kanji.tsv").map(::File).first { it.exists() }
        KanjiStore.index(f.readText())
    }
    private val of: (String) -> List<String>? = { ch -> kanji[ch]?.let { Kanji.parse(it) }?.let(Readings::candidates) }

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
        assertEquals(listOf("部屋は", "部屋"), Readings.lookupForms("部屋", "昨日の部屋は、まだ"))
        assertEquals(listOf("明るく", "明る"), Readings.lookupForms("明るく", ""))
        assertEquals(listOf("電気"), Readings.lookupForms("電気", ""))
    }

    @Test
    fun findsWordsInSortedBytes() {
        val idx = WordIndex("今日\tきょう・こんにち\n部屋\tへや\n部屋割\tへやわり\n".toByteArray())
        assertEquals(listOf("へや"), idx.find("部屋"))
        assertEquals(listOf("きょう", "こんにち"), idx.find("今日"))
        assertNull(idx.find("部"))
        assertNull(idx.find("部屋割り"))
    }

    @Test
    fun shippedWordsHaveCommonReadings() {
        val f = listOf("src/main/assets/words.tsv", "app/src/main/assets/words.tsv").map(::File).first { it.exists() }
        val idx = WordIndex(f.readBytes())
        assertEquals(listOf("へや"), idx.find("部屋"))
        assertEquals(listOf("でんき"), idx.find("電気"))
        assertEquals("あたたか", idx.find("暖か")!!.first())
        assertNull(idx.find("暖かく"))
    }
}
