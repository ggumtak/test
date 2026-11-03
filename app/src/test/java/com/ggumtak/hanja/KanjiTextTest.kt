package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiTextTest {
    @Test
    fun findsDistinctKanjiInOrder() {
        assertEquals(listOf("母", "電", "気", "部", "屋", "明"), KanjiText.kanjiIn("母は電気をつけて、部屋を明るくしました。電気"))
    }

    @Test
    fun skipsKanaAndMarks() {
        assertEquals(emptyList<String>(), KanjiText.kanjiIn("ひらがな カタカナ 々 、。abc 한글"))
        assertFalse(KanjiText.isKanji('々'.code))
        assertTrue(KanjiText.isKanji('気'.code))
    }

    @Test
    fun keepsSupplementaryKanjiWhole() {
        assertEquals(listOf("𠀋", "気"), KanjiText.kanjiIn("𠀋気"))
    }

    @Test
    fun limitsCount() {
        assertEquals(KanjiText.MAX_KANJI, KanjiText.kanjiIn("一二三四五六七八九十").size)
    }

    @Test
    fun formatsKunReadings() {
        val kun = KanjiText.kunReadings("あ.かり・あか.るい・あか.るむ・-あ.け・あ.く・あ.くる・あ.かす", limit = 4)
        assertEquals(listOf(KanjiText.Kun("あ", "かり"), KanjiText.Kun("あか", "るい"), KanjiText.Kun("あか", "るむ"),
            KanjiText.Kun("あ", "く")), kun)
        assertEquals(listOf(KanjiText.Kun("き", "")), KanjiText.kunReadings("き"))
        // Only affix forms: kept, without the hyphen.
        assertEquals(listOf(KanjiText.Kun("こ", "む")), KanjiText.kunReadings("-こ.む"))
        assertEquals(emptyList<KanjiText.Kun>(), KanjiText.kunReadings(""))
    }

    @Test
    fun listsOnReadings() {
        assertEquals(listOf("メイ", "ミョウ"), KanjiText.onReadings("メイ・ミョウ・ミン", limit = 2))
        assertEquals(emptyList<String>(), KanjiText.onReadings(""))
    }

    @Test
    fun promptCarriesTheSentence() {
        assertEquals("Selected: 明", KanjiText.userPrompt("明"))
        assertEquals("Selected: 明\nSentence: 明るくしました", KanjiText.userPrompt("明", "明るくしました"))
        assertEquals("Selected: 明", KanjiText.userPrompt("明", "明"))
    }

    @Test
    fun chatPromptCarriesTheSelectionAndSentence() {
        val p = KanjiText.chatSystemPrompt("受付の案内", "入口には、受付の案内が貼ってあります。")
        assertTrue(p.endsWith("Selected: 受付の案内\nSentence: 入口には、受付の案内が貼ってあります。"))
        assertTrue(KanjiText.chatSystemPrompt("電気", "").endsWith("Selected: 電気"))
        assertFalse(KanjiText.chatSystemPrompt("", "").contains("Selected"))
        // English instructions, Korean answers.
        assertTrue(p.contains("natural Korean (해요체)"))
        assertTrue(KanjiText.SYSTEM_PROMPT.contains("natural Korean (해요체)"))
        // Both ask for the format the card renders, and leave the Hangul pronunciation to the app.
        for (prompt in listOf(p, KanjiText.SYSTEM_PROMPT)) {
            assertTrue(prompt.contains("電気(でんき)") && prompt.contains("never write Hangul pronunciations"))
        }
    }

    @Test
    fun cleansSelection() {
        assertEquals("母は 電気", KanjiText.clean("  母は\n\t電気 "))
        assertEquals("", KanjiText.clean(null))
        assertEquals(KanjiText.MAX_SELECTION, KanjiText.clean("あ".repeat(1000)).length)
    }

    @Test
    fun encodesDictionaryUrls() {
        assertEquals("https://hanja.dict.naver.com/#/search?query=%E6%B0%97", KanjiText.naverHanjaUrl("気"))
        assertEquals("https://ja.dict.naver.com/#/search?query=%E9%9B%BB%E6%B0%97%20%E3%81%A4",
            KanjiText.naverJapaneseUrl("電気 つ"))
    }
    @Test
    fun truncationDoesNotBreakSupplementaryCharacters() {
        val clean = KanjiText.clean("𠀋".repeat(400))
        assertEquals(KanjiText.MAX_SELECTION, clean.codePointCount(0, clean.length))
        assertEquals("𠀋".repeat(KanjiText.MAX_SELECTION), clean)
    }

}
