package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaHangulTest {
    @Test
    fun convertsReadings() {
        assertEquals("메이", KanaHangul.convert("メイ"))
        assertEquals("묘우", KanaHangul.convert("ミョウ"))
        assertEquals("아카루이", KanaHangul.convert("あかるい"))
        assertEquals("쇼우", KanaHangul.convert("しょう"))
        assertEquals("키", KanaHangul.convert("キ"))
    }

    @Test
    fun putsNAndSmallTsuUnderThePreviousSyllable() {
        assertEquals("민", KanaHangul.convert("ミン"))
        assertEquals("갓코우", KanaHangul.convert("がっこう"))
        assertEquals("니혼", KanaHangul.convert("にほん"))
        assertEquals("ㄴ", KanaHangul.convert("ん"))
    }

    @Test
    fun handlesLoanwordSounds() {
        assertEquals("코-히-", KanaHangul.convert("コーヒー"))
        assertEquals("파-티-", KanaHangul.convert("パーティー"))
        assertEquals("테레비", KanaHangul.convert("テレビ"))
        assertEquals("화이루", KanaHangul.convert("ファイル"))
    }

    @Test
    fun keepsOtherCharacters() {
        assertEquals("明루이", KanaHangul.convert("明るい"))
        assertEquals("아 · 이", KanaHangul.convert("あ · い"))
    }

    @Test
    fun detectsKanaOnly() {
        assertTrue(KanaHangul.kanaOnly("テレビ"))
        assertTrue(KanaHangul.kanaOnly("ありがとう。"))
        assertFalse(KanaHangul.kanaOnly("電気"))
        assertFalse(KanaHangul.kanaOnly("明るい"))
        assertFalse(KanaHangul.kanaOnly("。"))
    }
}
