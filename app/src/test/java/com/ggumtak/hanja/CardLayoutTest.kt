package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {
    @Test
    fun choosesByTheWindow() {
        // Phones: one column upright and on their side (S23 Ultra about 412 x 915 dp).
        assertEquals(false, CardLayout.choose(412, 915, false).split)
        assertEquals(false, CardLayout.choose(915, 412, false).split)
        // Tab S7+ (about 800 x 1280 dp) both ways up: the pane, bigger on the wider window.
        val upright = CardLayout.choose(800, 1280, false)
        val wide = CardLayout.choose(1280, 800, false)
        assertTrue(upright.split && wide.split)
        assertEquals(CardLayout.PANE_MAX, wide.paneDp)
        assertTrue(upright.paneDp in CardLayout.PANE_MIN until CardLayout.PANE_MAX)
        // Half the tablet in split screen: the small pane while the text keeps its width.
        assertEquals(CardLayout.Choice(true, 175), CardLayout.choose(640, 800, false))
        // Narrower than that, or the keyboard open: one column.
        assertEquals(false, CardLayout.choose(560, 800, false).split)
        assertEquals(false, CardLayout.choose(1280, 800, true).split)
        // A short window keeps the drawing to the height it has.
        val short = CardLayout.choose(1000, 500, false)
        assertTrue(short.split && short.paneDp <= (500 * 0.62).toInt() - 90)
        // Whatever the window, the text keeps its minimum width beside the pane.
        for (w in 600..1400 step 10) for (h in 480..1400 step 40) {
            val c = CardLayout.choose(w, h, false)
            if (c.split) assertTrue("$w x $h", w - 56 - (c.paneDp + 32) >= CardLayout.TEXT_MIN)
        }
    }
}
