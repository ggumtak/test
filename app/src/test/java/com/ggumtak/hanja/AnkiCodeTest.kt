package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The loader in users' templates can never be changed by the app (some were pasted by hand), and a code file once
 * shipped under a number must keep its content: Anki would store new content under that name as a renamed copy
 * the loader never finds. tools/anki/code_versions.tsv pins both; a change to anki-tap.js needs a new number.
 */
class AnkiCodeTest {
    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private val pinned = File("../tools/anki/code_versions.tsv").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore('\t') to it.substringAfter('\t') }

    @Test
    fun loaderNeverChanges() {
        assertEquals(pinned["loader"], sha(File("src/main/assets/anki-loader.html")))
    }

    @Test
    fun codeFileMatchesItsNumber() {
        val js = File("src/main/assets/anki-tap.js")
        assertEquals("anki-tap.js changed: raise AnkiCode.VERSION and pin the new number", pinned[AnkiCode.VERSION.toString()], sha(js))
        val text = js.readText()
        assertTrue(text.contains("[${AnkiCode.VERSION}] = function ()"))
        assertTrue(text.contains(AnkiCode.MEDIA_NAME + ".js"))
        // The loader starts at the first number shipped this way; older numbers never existed as files.
        assertTrue(File("src/main/assets/anki-loader.html").readText().contains("probe(8)"))
        assertTrue(!text.contains("</script>"))
    }
}
