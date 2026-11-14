package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The dictionaries as the APK carries them (packed by buildSrc/PackTsv.java; the build puts them in the directory
 * the "packedAssets" property names) against their sources in src/main/assets: every key of every dictionary must
 * give exactly the value its line has, and nothing else may be found.
 */
class PackedTsvTest {
    private val names = listOf("parts", "words", "kanji", "origin", "usage", "expand", "similar", "radkanji", "extra")

    private fun packedDir(): File {
        val dir = System.getProperty("packedAssets")
        assertNotNull("the build did not say where the packed dictionaries are (packedAssets)", dir)
        return File(dir!!)
    }

    @Test
    fun everyLineOfEveryDictionaryIsFound() {
        val dir = packedDir()
        for (name in names) {
            val source = File("src/main/assets/$name.tsv")
            val bytes = File(dir, "$name.pk").readBytes()
            // One block kept: every change of block inflates again, as a cold lookup does.
            for (keep in listOf(1, 8)) {
                val packed = PackedTsv(java.nio.ByteBuffer.wrap(bytes), keep)
                var lines = 0
                source.forEachLine(Charsets.UTF_8) { line ->
                    if (line.isEmpty()) return@forEachLine
                    val tab = line.indexOf('\t')
                    val key = if (tab < 0) line else line.substring(0, tab)
                    val value = if (tab < 0) "" else line.substring(tab + 1)
                    assertEquals("$name: $key", value, packed.find(key))
                    lines++
                }
                assertTrue("$name is empty", lines > 0)
            }
        }
    }

    @Test
    fun whatIsNotThereIsNotFound() {
        val dir = packedDir()
        for (name in names) {
            val plain = SortedTsv(File("src/main/assets/$name.tsv").readBytes())
            val packed = PackedTsv(File(dir, "$name.pk").readBytes())
            val keys = ArrayList<String>()
            File("src/main/assets/$name.tsv").forEachLine(Charsets.UTF_8) { if (it.isNotEmpty()) keys.add(it.substringBefore('\t')) }
            // Around every 97th key and at both ends: a prefix, a longer key, the neighbours in byte order.
            val probes = ArrayList<String>()
            for (i in keys.indices step 97) {
                val k = keys[i]
                probes += listOf(k + "x", k + "\u0000", k.dropLast(1), k + k, "\u0001$k")
            }
            probes += listOf("", " ", "\u0000", "￿", "zzzz", keys.first().dropLast(1), keys.last() + "￿")
            for (p in probes) assertEquals("$name: \"$p\"", plain.find(p), packed.find(p))
            assertNull(packed.find(""))
        }
    }

    @Test
    fun theBigDictionariesShrink() {
        val dir = packedDir()
        for (name in listOf("parts", "words", "kanji")) {
            val source = File("src/main/assets/$name.tsv").length()
            val packed = File(dir, "$name.pk").length()
            assertTrue("$name: $source -> $packed", packed * 2 < source + source / 5)
        }
    }

    @Test
    fun aDamagedFileIsRefused() {
        val bytes = File(packedDir(), "similar.pk").readBytes()
        assertTrue(runCatching { PackedTsv(bytes.copyOf(8)) }.isFailure)
        assertTrue(runCatching { PackedTsv(ByteArray(64)) }.isFailure)
    }
}
