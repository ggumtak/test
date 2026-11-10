package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadicalKanjiTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    private val lines: Map<String, String> by lazy {
        asset("radkanji.tsv").readLines().filter { it.isNotEmpty() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
    }

    @Test
    fun listsTheOthersWithoutTheKanjiItself() {
        assertEquals("85氵", RadicalKanji.key(85, "氵"))
        assertEquals(listOf("池", "汽", "活"), RadicalKanji.mates("池汽活海決油波", "海", limit = 3))
        assertEquals(listOf("池", "汽", "活", "決", "油", "波"), RadicalKanji.mates("池汽活海決油波注", "海"))
        assertEquals(emptyList<String>(), RadicalKanji.mates(null, "海"))
        assertEquals(listOf("𠮟"), RadicalKanji.mates("𠮟叱", "叱"))
    }

    @Test
    fun theWaterKanjiAreUnderTheWaterRadical() {
        val water = RadicalKanji.mates(lines["85氵"], "", limit = 20)
        assertTrue(water.containsAll(listOf("池", "海", "泳")))
        // 酒's radical is 酉, and 水 itself is not a kanji "with" 氵.
        assertFalse("酒" in water)
        assertFalse("水" in water)
        // A kanji a dictionary only files under a radical is not an example of it: 東 and 来 under 木.
        val tree = RadicalKanji.mates(lines["75木"], "", limit = 20)
        assertTrue(tree.containsAll(listOf("村", "林", "校")))
        assertFalse("東" in tree)
        assertFalse("来" in tree)
    }

    @Test
    fun theDataIsWellFormed() {
        val joyo = asset("kanji.tsv").readLines().mapNotNull { Kanji.parse(it) }
            .filter { it.grade in 1..6 || it.grade == 8 }.associateBy { it.ch }
        val parts = HashMap<String, Parts>()
        asset("parts.tsv").forEachLine { line ->
            val ch = line.substringBefore('\t')
            if (ch in joyo) Parts.parse(line)?.let { parts[ch] = it }
        }
        assertTrue(lines.size in 60..200)
        for ((key, value) in lines) {
            val members = RadicalKanji.mates(value, "", limit = 99)
            assertTrue(key, members.size in 2..12)
            assertEquals(key, members.size, members.toSet().size)
            for (m in members) {
                val p = parts.getValue(m)
                assertEquals("$m under $key", key, RadicalKanji.key(p.radical, p.form))
            }
            // The easiest first: by school grade.
            assertEquals(key, members.map { joyo.getValue(it).grade }.sorted(), members.map { joyo.getValue(it).grade })
        }
        val keys = asset("radkanji.tsv").readLines().filter { it.isNotEmpty() }.map { it.substringBefore('\t') }
        assertEquals(keys.sortedWith { a, b -> compare(a.toByteArray(), b.toByteArray()) }, keys)
        assertEquals(lines["85氵"], SortedTsv(asset("radkanji.tsv").readBytes()).find("85氵"))
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
