package com.ggumtak.hanja

/**
 * How the lookup card lays itself out in the window it has now: what the app may use, not what the device is (a
 * tablet in split screen gets half the width, a phone on its side little height). Chosen again whenever the window
 * changes size or the keyboard opens or closes; the numbers are a start to tune on real devices.
 *
 *  - Narrow window (under [SPLIT_WIDTH] dp), low window (under [MIN_HEIGHT] dp: a phone on its side) or the keyboard
 *    open: one column, the stroke order opened under a kanji's row by a tap on it.
 *  - Wider: the text on the left and a stroke-order pane on the right that stays put while the text scrolls, but
 *    only while the text keeps [TEXT_MIN] dp to read in; the pane's drawing grows with the window from [PANE_MIN]
 *    to [PANE_MAX] dp and never takes more of the height than the card can give it.
 */
object CardLayout {
    const val SPLIT_WIDTH = 600
    const val MIN_HEIGHT = 480
    const val TEXT_MIN = 360
    const val PANE_MIN = 150
    const val PANE_MAX = 280

    /** The window's side margins and the card's padding (10 + 18 on each side). */
    private const val CHROME = 56
    /** The pane's own padding (8 + 8) and its gap from the text (16). */
    private const val PANE_EXTRA = 32
    /** Under the drawing: the hint and the kanji buttons. */
    private const val PANE_BELOW = 90
    /** The part of the window height the card's text area may take (CappedScrollView in LookupActivity). */
    private const val HEIGHT_SHARE = 0.62f

    /** [split]: text and pane side by side; [paneDp]: the side of the pane's drawing (0 for one column). */
    data class Choice(val split: Boolean, val paneDp: Int)

    fun choose(widthDp: Int, heightDp: Int, keyboard: Boolean): Choice {
        val one = Choice(false, 0)
        if (keyboard || widthDp < SPLIT_WIDTH || heightDp < MIN_HEIGHT) return one
        val content = widthDp - CHROME
        val tallest = (heightDp * HEIGHT_SHARE).toInt() - PANE_BELOW
        if (tallest < PANE_MIN) return one
        var pane = (content * 0.3f).toInt().coerceIn(PANE_MIN, PANE_MAX).coerceAtMost(tallest)
        if (content - (pane + PANE_EXTRA) < TEXT_MIN) pane = PANE_MIN
        if (content - (pane + PANE_EXTRA) < TEXT_MIN) return one
        return Choice(true, pane)
    }
}
