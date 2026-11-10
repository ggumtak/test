package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KanaHangulTest {
    @Test
    fun convertsReadings() {
        assertEquals("메-", KanaHangul.convert("メイ"))
        assertEquals("묘-", KanaHangul.convert("ミョウ"))
        assertEquals("아카루이", KanaHangul.convert("あかるい"))
        assertEquals("쇼-", KanaHangul.convert("しょう"))
        assertEquals("키", KanaHangul.convert("キ"))
    }

    @Test
    fun putsNAndSmallTsuUnderThePreviousSyllable() {
        assertEquals("민", KanaHangul.convert("ミン"))
        assertEquals("갓코-", KanaHangul.convert("がっこう"))
        assertEquals("니혼", KanaHangul.convert("にほん"))
        assertEquals("ㄴ", KanaHangul.convert("ん"))
    }

    @Test
    fun writesALongVowelAsADash() {
        // The second kana only lengthens the vowel before it: "코우" would teach a Korean reader to say the う.
        assertEquals("코-", KanaHangul.convert("コウ"))
        assertEquals("센세-", KanaHangul.convert("せんせい"))
        assertEquals("쿠-키", KanaHangul.convert("くうき"))
        assertEquals("오카-산", KanaHangul.convert("おかあさん"))
        assertEquals("오니-산", KanaHangul.convert("おにいさん"))
        assertEquals("오네-산", KanaHangul.convert("おねえさん"))
        assertEquals("토-", KanaHangul.convert("とお"))
        assertEquals("쿄-", KanaHangul.convert("きょう"))
        assertEquals("주-", KanaHangul.convert("ジュウ"))
        assertEquals("아리가토-", KanaHangul.convert("ありがとう"))
        // Not a long vowel: other vowel pairs, and a vowel after ん or っ.
        assertEquals("아이", KanaHangul.convert("あい"))
        assertEquals("코이", KanaHangul.convert("こい"))
        assertEquals("카오", KanaHangul.convert("かお"))
        assertEquals("우에", KanaHangul.convert("うえ"))
        assertEquals("스이", KanaHangul.convert("スイ"))
        assertEquals("혼오", KanaHangul.convert("ほんお"))
        // Never three in a row.
        assertEquals("오-우", KanaHangul.convert("おおう"))
    }

    @Test
    fun keepsTheVowelAcrossABoundaryTheCallerKnows() {
        // 思う: the stem おも and the okurigana う; the う is said.
        assertEquals("오모우", KanaHangul.convertParts(listOf("おも", "う")))
        assertEquals("오-키-", KanaHangul.convertParts(listOf("おお", "きい")))
        // 子牛 こ + うし; 学校 がっ + こう: っ still closes the syllable before it, across the boundary too.
        assertEquals("코우시", KanaHangul.convertParts(listOf("こ", "うし")))
        assertEquals("갓코-", KanaHangul.convertParts(listOf("がっ", "こう")))
        assertEquals("핫테", KanaHangul.convertParts(listOf("は", "って")))
        assertEquals("타베루", KanaHangul.convertParts(listOf("た", "べる")))
        // A verb written in kana alone, where no kanji shows the boundary.
        assertEquals("오모우", KanaHangul.convert("おもう"))
        assertEquals("마요우", KanaHangul.convert("まよう"))
        // そう, よう are far more often the long-vowel words.
        assertEquals("소-", KanaHangul.convert("そう"))
    }

    @Test
    fun splitsAWordByItsCharacters() {
        // Readings.split's result: one reading per character; the kana between kanji are one stretch.
        assertEquals("오모우", KanaHangul.ofWord("思う", listOf("おも", "う"), "おもう"))
        assertEquals("오-키-", KanaHangul.ofWord("大きい", listOf("おお", "き", "い"), "おおきい"))
        assertEquals("코우시", KanaHangul.ofWord("子牛", listOf("こ", "うし"), "こうし"))
        assertEquals("갓코-", KanaHangul.ofWord("学校", listOf("がっ", "こう"), "がっこう"))
        assertEquals("이이카타", KanaHangul.ofWord("言い方", listOf("い", "い", "かた"), "いいかた"))
        // Read as a whole (no split): one stretch.
        assertEquals("쿄-", KanaHangul.ofWord("今日", null, "きょう"))
        // A split that does not fit the word is not trusted.
        assertEquals("쿄-", KanaHangul.ofWord("今日", listOf("きょう"), "きょう"))
    }

    @Test
    fun theKanaOnlyVerbsAreRealVerbs() {
        // Each exception is a 상용한자 kun reading "stem.う": the list cannot drift into ordinary long-vowel words.
        val file = listOf("src/main/assets/kanji.tsv", "app/src/main/assets/kanji.tsv").map { File(it) }.first { it.exists() }
        val verbs = file.readLines().mapNotNull { Kanji.parse(it) }.flatMap { k ->
            KanjiText.kunReadings(k.kun, 40).filter { it.okurigana == "う" }.map { it.stem + "う" }
        }.toSet()
        for (v in listOf("おもう", "まよう", "さそう", "ひろう", "かよう", "ととのう", "ぬう", "におう")) {
            assertTrue(v, v in verbs)
            assertTrue(v, KanaHangul.convert(v).endsWith("우"))
        }
    }

    @Test
    fun handlesLoanwordSounds() {
        assertEquals("코-히-", KanaHangul.convert("コーヒー"))
        assertEquals("파-티-", KanaHangul.convert("パーティー"))
        assertEquals("테레비", KanaHangul.convert("テレビ"))
        assertEquals("화이루", KanaHangul.convert("ファイル"))
    }

    @Test
    fun readsTheParticleHaAsWa() {
        assertEquals("콘니치와", KanaHangul.convert("こんにちは"))
        assertEquals("콘반와", KanaHangul.convert("こんばんは"))
        assertEquals("하하", KanaHangul.convert("はは"))
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
