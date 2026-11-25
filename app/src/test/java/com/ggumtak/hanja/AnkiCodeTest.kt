package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The loader in users' templates can never be changed by the app (some are pasted by hand), so it is pinned in
 * tools/anki/code_versions.tsv. The code changes freely: it lives in the note type's CSS (AnkiCss).
 */
class AnkiCodeTest {
    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private val pinned = File("../tools/anki/code_versions.tsv").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore('\t') to it.substringAfter('\t') }

    private val script = File("src/main/assets/anki-tap.js").readBytes()

    @Test
    fun loaderNeverChanges() {
        assertEquals(pinned["loader"], sha(File("src/main/assets/anki-loader.html")))
    }

    @Test
    fun cssBlockCarriesTheCode() {
        val block = AnkiCss.block(script)
        val b64 = Regex("""--hjtap-code:"([A-Za-z0-9+/=]+)"""").find(block)!!.groupValues[1]
        assertTrue(Base64.getDecoder().decode(b64).contentEquals(script))
        // Nothing in the block can end the CSS comment or string early.
        assertEquals(2, Regex(Regex.escape("*/")).findAll(block).count())
    }

    @Test
    fun cssKeepsTheDecksStylingAndHoldsOneBlock() {
        val block = AnkiCss.block(script)
        val deck = ".card { font-size: 20px; }\n.kanji { color: red; }"
        val once = AnkiCss.install(deck, block)
        assertTrue(once.startsWith(deck))
        assertTrue(AnkiCss.isCurrent(once, block))
        val old = AnkiCss.block("old".toByteArray())
        assertFalse(AnkiCss.isCurrent(AnkiCss.install(deck, old), block))
        val again = AnkiCss.install(AnkiCss.install(deck, old) + "\n.after { x: 1 }", block)
        assertEquals(1, again.split(AnkiCss.START).size - 1)
        assertTrue(again.contains(".after { x: 1 }") && again.contains(".kanji { color: red; }"))
        assertEquals(deck, AnkiCss.strip(once))
        // A block cut off before its end: the rest goes, the deck's own styling stays.
        assertEquals(deck, AnkiCss.strip(deck + "\n" + block.substring(0, 60)))
        assertEquals(block + "\n", AnkiCss.install("", block))
    }
}
