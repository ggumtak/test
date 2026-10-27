package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KanjiTest {
    @Test
    fun parsesLine() {
        val k = Kanji.parse("気\t기운 기\t氣\tキ・ケ\tき\t6\t1\tspirit; mind; air")!!
        assertEquals("기운 기", k.hun)
        assertEquals("氣", k.variant)
        assertEquals(6, k.strokes)
        assertEquals("초등 1학년", k.gradeLabel)
        assertEquals("spirit, mind, air", k.meanings)
    }

    @Test
    fun kokujiAndEmptyFields() {
        val k = Kanji.parse("働\t\t\tドウ\tはたら.く\t13\t4\twork; (kokuji)")!!
        assertTrue(k.kokuji)
        assertEquals("work", k.meanings)
        assertEquals("초등 4학년", k.gradeLabel)
        val bare = Kanji.parse("乶\t볼 볼\t\t\t\t\t\t")!!
        assertEquals(0, bare.strokes)
        assertEquals("", bare.gradeLabel)
    }

    @Test
    fun rejectsBrokenLines() {
        assertNull(Kanji.parse(""))
        assertNull(Kanji.parse("気\t기운 기"))
    }

    @Test
    fun indexesByCharacter() {
        val map = KanjiStore.index("気\ta\n𠀋\tb\n\n気\tdup\n")
        assertEquals("気\ta", map["気"])
        assertEquals("𠀋\tb", map["𠀋"])
        assertEquals(2, map.size)
    }

    /** The shipped data keeps the shinjitai → traditional 훈음 links the card depends on. */
    @Test
    fun shippedDataHasKoreanReadings() {
        val file = listOf("src/main/assets/kanji.tsv", "app/src/main/assets/kanji.tsv").map(::File).first { it.exists() }
        val map = KanjiStore.index(file.readText())
        fun k(ch: String) = Kanji.parse(map.getValue(ch))!!
        assertEquals("기운 기", k("気").hun)
        assertEquals("氣", k("気").variant)
        assertEquals("넓을 광", k("広").hun)
        assertEquals("밝을 명", k("明").hun)
        assertTrue(k("円").hun.contains("둥글 원"))
        assertTrue(k("芸").hun.startsWith("재주 예"))
        assertTrue(k("働").kokuji)
        assertTrue(map.size > 13000)
    }
}
