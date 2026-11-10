package com.ggumtak.hanja

/**
 * Recall before reading: a word the user has looked up before opens with its readings and meanings covered, so
 * they first try to remember them and then tap to check. Trying to retrieve an answer, even failing, strengthens
 * the memory more than reading it again; a first lookup, where there is nothing to retrieve yet, shows everything
 * at once as before.
 */
object Recall {
    /** [times] the word has been looked up, this one included; [cover] it now; [stored] goes back to the settings. */
    class Look(val times: Int, val cover: Boolean, val stored: String)

    /**
     * One more look at a word whose earlier looks are [stored] (null: never). Opening the card again within ten
     * minutes is the same look (VocabNote.count): the answer was on screen a moment ago, so nothing is covered.
     */
    fun look(stored: String?, now: Long): Look {
        val (n, next) = VocabNote.count(stored, now)
        val counted = next != stored
        return Look(n, counted && n >= 2, next)
    }

    /** What the cover says. */
    fun prompt(times: Int): String = "${times}번째 찾아봅니다. 읽기와 뜻을 먼저 떠올린 뒤 여기를 눌러 확인하세요  ▸"
}
