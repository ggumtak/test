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
}
