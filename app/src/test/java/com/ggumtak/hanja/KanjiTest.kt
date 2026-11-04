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
    fun findsLinesInSortedBytes() {
        // Sorted by UTF-8 bytes: 気 (E6 B0 97) before 𠀋 (F0 A0 80 8B).
        val tsv = SortedTsv("気\ta\tb\n気候\tc\n𠀋\td\n".toByteArray())
        assertEquals("a\tb", tsv.find("気"))
        assertEquals("c", tsv.find("気候"))
        assertEquals("d", tsv.find("𠀋"))
        assertNull(tsv.find("気温"))
        assertNull(tsv.find(""))
        assertNull(SortedTsv(ByteArray(0)).find("気"))
    }

    @Test
    fun parsesGlossColumns() {
        val k = Kanji.parse("駅\t잇닿을 역\t驛\tエキ\t\t14\t3\tstation\t역(정거장)\t역")!!
        assertEquals("역(정거장)", k.gloss)
        assertEquals("역", k.englishKo)
        assertEquals("", Kanji.parse("気\t기운 기\t氣\tキ\tき\t6\t1\tspirit")!!.gloss)
        assertEquals("곳 소, 바 소", Kanji.parse("所\t바 소, 곳 소\t\tショ\tところ\t8\t3\tplace\t곳\t곳\t곳 소, 바 소")!!.hunJa)
    }

    /** The shipped data keeps the shinjitai → traditional 훈음 links the card depends on. */
    @Test
    fun shippedDataHasKoreanReadings() {
        val file = listOf("src/main/assets/kanji.tsv", "app/src/main/assets/kanji.tsv").map(::File).first { it.exists() }
        val tsv = SortedTsv(file.readBytes())
        fun k(ch: String) = Kanji.parse(ch + "\t" + tsv.find(ch)!!)!!
        assertEquals("기운 기", k("気").hun)
        assertEquals("氣", k("気").variant)
        assertEquals("鍊", k("錬").variant)
        assertEquals("넓을 광", k("広").hun)
        assertEquals("밝을 명", k("明").hun)
        assertTrue(k("円").hun.contains("둥글 원"))
        assertTrue(k("芸").hun.startsWith("재주 예"))
        assertTrue(k("働").kokuji)
        assertTrue(file.readLines().size > 13000)
        // Plain-Korean meanings for every 상용한자, most common use first.
        assertEquals("역(정거장)", k("駅").gloss)
        assertTrue(k("円").gloss.startsWith("엔"))
        assertTrue(k("所").gloss.startsWith("곳"))
        assertEquals("일하다", k("働").gloss)
        val missing = file.readLines().map { it.split('\t') }
            .filter { it.size >= 9 && it[6].toIntOrNull() in 1..8 && it[8].isEmpty() }.map { it[0] }
        assertEquals(emptyList<String>(), missing)
        // 훈음 with the one natural for Japanese first.
        assertTrue(k("所").hunJa.startsWith("곳 소"))
        assertEquals("붙일 첩", k("貼").hunJa)
        assertEquals("역 역", k("駅").hunJa)
        val noHun = file.readLines().map { it.split('\t') }
            .filter { it.size >= 9 && it[6].toIntOrNull() in 1..8 && it.getOrElse(10) { "" }.isEmpty() }.map { it[0] }
        assertEquals(emptyList<String>(), noHun)
        assertEquals("갈림길 기", k("岐").hun.substringBefore(","))
    }
}
