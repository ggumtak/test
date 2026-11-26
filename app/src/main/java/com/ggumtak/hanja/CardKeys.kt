package com.ggumtak.hanja

import android.view.KeyEvent

/**
 * The card's keys for a hardware (Bluetooth) keyboard, so a lookup is read and closed without touching the screen
 * (2026-11-24, 사용자 요청). The card is its own window: while it is open AnkiDroid hears none of these keys, so they
 * may reuse the keys of Anki's study screen. The left hand stays where it rates Anki cards (1–4, Space, Q/W):
 * the four buttons at the bottom are 1 2 3 4 from left to right, 예문 보기 next to them is 5, W/S scroll up/down
 * (Shift+W/S by page), A/D/F trigger 🔊/☆/한, and Q, the key that opens the card in Anki (사용자 행동 1), closes it.
 */
object CardKeys {
    enum class Action {
        CLOSE, JAPANESE_DICT, HANJA_DICT, AI, ASK, EXAMPLES, SPEAK, HANGUL, VOCAB, STROKES,
        SCROLL_UP, SCROLL_DOWN, PAGE_UP, PAGE_DOWN, HELP,
        /** In the question input. */
        SEND, LEAVE_INPUT,
    }

    /**
     * What the key [keyCode] does, or null when the card leaves it to Android (Tab and Enter move between and press
     * items; letters go into the question input). [typing]: the question input has the focus. [modified]: Ctrl, Alt
     * or Meta is held, which no card key uses (they stay the system's).
     */
    fun action(keyCode: Int, shift: Boolean, modified: Boolean, typing: Boolean): Action? {
        if (typing) return when {
            keyCode == KeyEvent.KEYCODE_ESCAPE -> Action.LEAVE_INPUT
            (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && !shift -> Action.SEND
            else -> null
        }
        if (modified) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_SPACE -> Action.CLOSE
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> Action.JAPANESE_DICT
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> Action.HANJA_DICT
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> Action.AI
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> Action.ASK
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> Action.EXAMPLES
            KeyEvent.KEYCODE_A -> Action.SPEAK
            KeyEvent.KEYCODE_D -> Action.VOCAB
            KeyEvent.KEYCODE_F -> Action.HANGUL
            KeyEvent.KEYCODE_R -> Action.STROKES
            KeyEvent.KEYCODE_W -> if (shift) Action.PAGE_UP else Action.SCROLL_UP
            KeyEvent.KEYCODE_S -> if (shift) Action.PAGE_DOWN else Action.SCROLL_DOWN
            KeyEvent.KEYCODE_DPAD_UP -> Action.SCROLL_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Action.SCROLL_DOWN
            KeyEvent.KEYCODE_PAGE_UP -> Action.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> Action.PAGE_DOWN
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_F1 -> Action.HELP
            else -> null
        }
    }

    /** Scrolling keys work held down; the others act once per press. */
    fun repeats(action: Action): Boolean =
        action == Action.SCROLL_UP || action == Action.SCROLL_DOWN || action == Action.PAGE_UP || action == Action.PAGE_DOWN

    /** The card's own list (/ on the card), and the help dialog's. */
    const val HELP = "Q · Esc · 스페이스  카드 닫기\n" +
        "W · ↑ 위로 스크롤 · S · ↓ 아래로 스크롤 (꾹 누르면 연속 이동)\n" +
        "Shift+W · PgUp 이전 페이지 · Shift+S · PgDn 다음 페이지\n" +
        "1 일본어사전 · 2 한자사전 · 3 AI 설명 · 4 질문 · 5 예문 보기\n" +
        "A 발음 듣기 · D 단어장(☆) · F 한글 발음\n" +
        "R 획순 재생 (누를 때마다 다음 한자)\n" +
        "Tab 항목 이동 · Enter 항목 열기\n" +
        "질문 입력 중: Enter 보내기 · Shift+Enter 줄바꿈 · Esc 입력 종료\n" +
        "/ 단축키 보기"

    /** AnkiDroid: one key per 사용자 행동 (설정 → 컨트롤 → 사용자 행동 N → 키 추가하기 → 앞면과 뒷면). */
    const val ANKI_HELP = "Q → 사용자 행동 1: 한자 카드 열기 (선택한 단어·획순 그림, 없으면 첫 항목)\n" +
        "W → 사용자 행동 2: 다음 단어·그림 선택\n" +
        "Shift+W → 사용자 행동 3: 이전 단어·그림 선택"
}
