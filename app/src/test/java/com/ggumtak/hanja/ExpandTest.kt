package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** assets/expand.tsv (tools/build_expand_data.py): the 확장 row, the kanji to learn with each 상용한자. */
class ExpandTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    private val lines: List<String> by lazy { asset("expand.tsv").readLines().filter { it.isNotEmpty() } }

    private val expand: Map<String, List<String>> by lazy {
        lines.associate { it.substringBefore('\t') to Similar.parse(it.substringAfter('\t')) }
    }

    private val joyo: Set<String> by lazy {
        asset("kanji.tsv").readLines().mapNotNull { Kanji.parse(it) }
            .filter { it.grade in 1..6 || it.grade == 8 }.map { it.ch }.toSet()
    }

    @Test
    fun theDataIsWellFormedAndSortedForTheBinarySearch() {
        assertTrue(expand.size in 1200..2136)
        for ((ch, list) in expand) {
            assertTrue(ch, ch in joyo)
            assertTrue(ch, list.isNotEmpty() && list.size <= 3 && list.all { it in joyo })
            assertFalse("$ch lists itself", ch in list)
        }
        val keys = lines.map { it.substringBefore('\t').toByteArray(Charsets.UTF_8) }
        for (i in 1 until keys.size) assertTrue(compareBytes(keys[i - 1], keys[i]) < 0)
    }

    @Test
    fun theKanjiToLearnTogether() {
        // The kanji that have it as their sound part (真: 慎 鎮; 青: 晴 清 情).
        assertEquals(listOf("慎", "鎮"), expand.getValue("真").take(2))
        assertTrue(expand.getValue("青").containsAll(listOf("晴", "清", "情")))
        // The same sound part and reading first (持: 寺 時 詩).
        assertEquals("寺", expand.getValue("持")[0])
        // No row of trivia for a one-stroke kanji.
        assertFalse("一" in expand)
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 255) - (b[i].toInt() and 255)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
