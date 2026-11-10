package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallTest {
    private val minute = 60_000L

    @Test
    fun aFirstLookShowsEverything() {
        val first = Recall.look(null, 1_000_000L)
        assertEquals(1, first.times)
        assertFalse(first.cover)
    }

    @Test
    fun aLaterLookIsCoveredAndCounted() {
        val t = 1_000_000L
        val first = Recall.look(null, t)
        val second = Recall.look(first.stored, t + 11 * minute)
        assertEquals(2, second.times)
        assertTrue(second.cover)
        val third = Recall.look(second.stored, t + 3 * 24 * 60 * minute)
        assertEquals(3, third.times)
        assertTrue(third.cover)
        assertTrue(Recall.prompt(third.times).startsWith("3번째"))
    }

    @Test
    fun openingAgainWithinTenMinutesIsTheSameLook() {
        val t = 1_000_000L
        val first = Recall.look(null, t)
        // Closed by mistake and opened again: nothing to recall, the answer was just on screen.
        val again = Recall.look(first.stored, t + 2 * minute)
        assertEquals(1, again.times)
        assertFalse(again.cover)
        val second = Recall.look(again.stored, t + 20 * minute)
        assertTrue(second.cover)
        // Reopened right after the covered look was revealed: not covered a second time.
        val reopened = Recall.look(second.stored, t + 21 * minute)
        assertEquals(2, reopened.times)
        assertFalse(reopened.cover)
    }
}
