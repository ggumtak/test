package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiTargetTest {
    private val ad = AnkiTarget.ANKIDROID.pkg
    private val my = AnkiTarget.MYAMGI.pkg
    private val comet = AnkiTarget.MYAMGI_COMET.pkg

    @Test
    fun onlyOneInstalledIsUsed() {
        assertEquals(AnkiTarget.ANKIDROID, AnkiTarget.choose(null, setOf(ad)).target)
        assertEquals(AnkiTarget.MYAMGI, AnkiTarget.choose(null, setOf(my)).target)
        assertEquals(AnkiTarget.MYAMGI_COMET, AnkiTarget.choose(null, setOf(comet)).target)
    }

    @Test
    fun myAmgiIsTheDefaultBesideAnkiDroidAndSaysSo() {
        val c = AnkiTarget.choose(null, setOf(ad, my))
        assertEquals(AnkiTarget.MYAMGI, c.target)
        assertFalse(c.chosenByUser)
        assertTrue(c.note.contains("myAmgi와 AnkiDroid가 모두 설치되어"))
        assertEquals(AnkiTarget.MYAMGI_COMET, AnkiTarget.choose(null, setOf(ad, comet)).target)
    }

    @Test
    fun theUsersChoiceWinsWhileInstalled() {
        val c = AnkiTarget.choose(ad, setOf(ad, my))
        assertEquals(AnkiTarget.ANKIDROID, c.target)
        assertTrue(c.chosenByUser)
        // Chosen app removed: back to what is installed.
        assertEquals(AnkiTarget.MYAMGI, AnkiTarget.choose(ad, setOf(my)).target)
        // An unknown saved value is ignored.
        assertEquals(AnkiTarget.ANKIDROID, AnkiTarget.choose("x.y", setOf(ad)).target)
    }

    @Test
    fun noneInstalled() {
        val c = AnkiTarget.choose(my, emptySet())
        assertNull(c.target)
        assertTrue(c.note.isNotEmpty())
    }

    @Test
    fun addressesFollowEachApp() {
        // AnkiDroid unchanged; myAmgi's authority and permission as its manifest declares them (never renamed).
        assertEquals("com.ichi2.anki.flashcards", AnkiTarget.ANKIDROID.authority)
        assertEquals("com.ichi2.anki.permission.READ_WRITE_DATABASE", AnkiTarget.ANKIDROID.permission)
        assertEquals("com.ggumtak.myamgi.flashcards", AnkiTarget.MYAMGI.authority)
        assertEquals("com.ggumtak.myamgi.permission.READ_WRITE_DATABASE", AnkiTarget.MYAMGI.permission)
        assertEquals("com.ggumtak.myamgi.comet.flashcards", AnkiTarget.MYAMGI_COMET.authority)
        assertEquals("com.ggumtak.myamgi.comet.permission.READ_WRITE_DATABASE", AnkiTarget.MYAMGI_COMET.permission)
        assertTrue(AnkiTarget.ANKIDROID.needsTemplateCode)
        assertFalse(AnkiTarget.MYAMGI.needsTemplateCode)
        assertFalse(AnkiTarget.MYAMGI_COMET.needsTemplateCode)
    }

    @Test
    fun manifestDeclaresEveryTarget() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        for (t in AnkiTarget.entries) {
            assertTrue("uses-permission ${t.permission}", manifest.contains("<uses-permission android:name=\"${t.permission}\""))
            assertTrue("queries ${t.pkg}", manifest.contains("<package android:name=\"${t.pkg}\""))
        }
    }
}
