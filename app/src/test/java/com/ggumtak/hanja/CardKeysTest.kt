package com.ggumtak.hanja

import android.view.KeyEvent
import com.ggumtak.hanja.CardKeys.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardKeysTest {
    private fun key(code: Int, shift: Boolean = false, modified: Boolean = false, typing: Boolean = false) =
        CardKeys.action(code, shift, modified, typing)

    @Test
    fun bottomButtonsAreOneToFourLeftToRight() {
        assertEquals(Action.JAPANESE_DICT, key(KeyEvent.KEYCODE_1))
        assertEquals(Action.HANJA_DICT, key(KeyEvent.KEYCODE_2))
        assertEquals(Action.AI, key(KeyEvent.KEYCODE_3))
        assertEquals(Action.ASK, key(KeyEvent.KEYCODE_4))
        assertEquals(Action.EXAMPLES, key(KeyEvent.KEYCODE_5))
        assertEquals(Action.ASK, key(KeyEvent.KEYCODE_NUMPAD_4))
    }

    @Test
    fun theKeyThatOpensInAnkiClosesTheCard() {
        for (k in listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE)) assertEquals(Action.CLOSE, key(k))
    }

    @Test
    fun typingKeepsLettersAndNumbersForTheQuestion() {
        for (k in listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_A)) {
            assertNull(key(k, typing = true))
        }
        assertEquals(Action.SEND, key(KeyEvent.KEYCODE_ENTER, typing = true))
        assertNull(key(KeyEvent.KEYCODE_ENTER, shift = true, typing = true))
        assertEquals(Action.LEAVE_INPUT, key(KeyEvent.KEYCODE_ESCAPE, typing = true))
    }

    @Test
    fun ctrlAndTabStayTheSystems() {
        assertNull(key(KeyEvent.KEYCODE_Z, modified = true))
        assertNull(key(KeyEvent.KEYCODE_1, modified = true))
        assertNull(key(KeyEvent.KEYCODE_TAB))
        assertNull(key(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun onlyScrollingRepeats() {
        assertEquals(true, CardKeys.repeats(Action.SCROLL_DOWN))
        assertEquals(false, CardKeys.repeats(Action.CLOSE))
    }
}
