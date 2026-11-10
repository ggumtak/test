package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimilarTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    private val similar: Map<String, List<String>> by lazy {
        asset("similar.tsv").readLines().filter { it.isNotEmpty() }.associate { line ->
            line.substringBefore('\t') to Similar.parse(line.substringAfter('\t'))
        }
    }

    private val joyo: Set<String> by lazy {
        asset("kanji.tsv").readLines().mapNotNull { Kanji.parse(it) }
            .filter { it.grade in 1..6 || it.grade == 8 }.map { it.ch }.toSet()
    }

    @Test
    fun parsesTheLookAlikes() {
        assertEquals(listOf("特", "侍", "待"), Similar.parse("特侍待"))
        assertEquals(emptyList<String>(), Similar.parse(null))
        assertEquals(emptyList<String>(), Similar.parse(""))
        // At most four, and a kanji outside the BMP is one character.
        assertEquals(listOf("一", "二", "三", "四"), Similar.parse("一二三四五"))
        assertEquals(listOf("𠮟", "叱"), Similar.parse("𠮟叱"))
    }

    @Test
    fun theDataIsWellFormed() {
        assertTrue(similar.size in 600..1400)
        for ((ch, list) in similar) {
            assertTrue(ch, ch in joyo)
            assertTrue(ch, list.isNotEmpty() && list.size <= Similar.MAX)
            assertFalse("$ch lists itself", ch in list)
            assertEquals("$ch repeats one", list.size, list.toSet().size)
            assertTrue("$ch: $list", list.all { it in joyo })
        }
        // Sorted by UTF-8 bytes, as the app's binary search needs.
        val keys = asset("similar.tsv").readLines().filter { it.isNotEmpty() }.map { it.substringBefore('\t') }
        assertEquals(keys.sortedWith { a, b -> compareBytes(a.toByteArray(), b.toByteArray()) }, keys)
        val tsv = SortedTsv(asset("similar.tsv").readBytes())
        assertEquals(similar.getValue("持"), Similar.parse(tsv.find("持")))
        assertEquals(emptyList<String>(), Similar.parse(tsv.find("愛")))
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    @Test
    fun theClassicPairsAreThere() {
        // Each of these is mixed up with the other, both ways.
        val pairs = listOf("未" to "末", "土" to "士", "大" to "犬", "日" to "目", "千" to "干", "牛" to "午", "人" to "入",
            "持" to "待", "持" to "特", "微" to "徴", "輸" to "輪", "緑" to "録", "貝" to "見", "石" to "右", "鏡" to "境",
            "熊" to "態", "幣" to "弊", "検" to "険", "腹" to "復", "礼" to "札", "村" to "材", "困" to "因", "減" to "滅",
            "忙" to "忘", "博" to "専", "遣" to "遺", "侯" to "候", "壁" to "璧", "衝" to "衡", "治" to "冶")
        val missing = pairs.filter { (a, b) -> b !in similar[a].orEmpty() || a !in similar[b].orEmpty() }
        assertEquals("missing: $missing", emptyList<Pair<String, String>>(), missing)
    }

    @Test
    fun aKanjiWithNoLookAlikeHasNoLine() {
        // Nothing among the 상용한자 is drawn like these.
        for (ch in listOf("愛", "気", "食", "一", "鬱")) assertFalse(ch, ch in similar)
    }
}
