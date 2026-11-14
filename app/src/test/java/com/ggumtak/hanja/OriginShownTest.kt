package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OriginShownTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    private fun units(spec: String) = Kosei.parse(spec)!!

    @Test
    fun leavesOutTheSentenceThe구성LineAlreadySays() {
        val sea = Origin("형성: 뜻은 氵(물), 소리는 毎(マイ). 본래 어두운 물, 큰 바다.", "", "^氵[뜻](물) + 毎[소리 マイ](매양 매)")
        assertEquals("본래 어두운 물, 큰 바다.", sea.shown(units(sea.kosei), listOf("바다 해")))
        // Without a 구성 line on the card nothing is repeated.
        assertEquals("뜻은 氵(물), 소리는 毎(マイ). 본래 어두운 물, 큰 바다.", sea.shown(null, listOf("바다 해")))
        // The other opening the notes use, and a sound part without parentheses.
        val bury = Origin("형성: 土(흙)이 뜻, 里가 소리. 본래 薶를 흔히 줄여 쓰던 글자. 흙 속에 묻음.", "",
            "^土[뜻](흙) + 里[소리 マイ](마을 리)")
        assertEquals("본래 薶를 흔히 줄여 쓰던 글자. 흙 속에 묻음.", bury.shown(units(bury.kosei), listOf("묻을 매")))
        // The gloss in the parentheses is the 구성 note's.
        val envy = Origin("형성: 뜻은 女(여자), 소리는 疾(シツ, 병·미워하다). 남을 미워하며 시샘함.", "",
            "^女[뜻](여자) + 疾[소리 シツ](병·미워하다)")
        assertEquals("남을 미워하며 시샘함.", envy.shown(units(envy.kosei), listOf("시기할 질")))
        // The 유래 names the full form, the 구성 line the shape drawn, whose note names the full form.
        val bad = Origin("형성: 뜻은 心(마음), 소리는 亞(ア). 악함·미워함. 일본에서는 亞를 亜로 줄여 씀.", "",
            "亜[소리 ア](亞를 줄인 꼴) + ^心[뜻](마음)")
        assertEquals("악함·미워함. 일본에서는 亞를 亜로 줄여 씀.", bad.shown(units(bad.kosei), listOf("악할 악")))
    }

    @Test
    fun keepsASentenceThatSaysMore() {
        // The full form of 月 (肉) is not on the 구성 line.
        val fat = Origin("형성: 뜻은 月(肉, 살), 소리는 方(ホウ). 기름, 비계.", "", "^月[뜻](살) + 方[소리 ホウ](방향 방)")
        assertEquals(fat.body, fat.shown(units(fat.kosei), listOf("기름 방")))
        // A hedge after the sentence's parts.
        val plain = Origin("형성: 뜻은 庚(악기, 广·⺕ 부분), 소리는 用(ヨウ)(의견이 갈림). 뒤에 평범함.", "",
            "^广+⺕[뜻](악기 庚의 부분) + 用[소리 ヨウ](쓸 용)")
        assertEquals(plain.body, plain.shown(units(plain.kosei), listOf("평범할 용")))
        // Another reading than the 구성 line's.
        val other = Origin("형성: 뜻은 氵(물), 소리는 毎(バイ). 바다.", "", "^氵[뜻](물) + 毎[소리 マイ](매양 매)")
        assertEquals(other.body, other.shown(units(other.kosei), listOf("바다 해")))
        // Not that opening at all.
        val gather = Origin("회의: 隹(새)+木(나무). 새가 나무 위에 모여 앉은 모양.", "", "^隹[뜻](새) + 木[뜻](나무)")
        assertEquals(gather.body, gather.shown(units(gather.kosei), listOf("모을 집")))
    }

    @Test
    fun dropsARowThatOnlyRepeatsTheMeaning() {
        fun rest(body: String, hun: String) =
            Origin("형성: 뜻은 氵(물), 소리는 皮(ヒ). $body", "", "^氵[뜻](물) + 皮[소리 ヒ](가죽 피)")
                .shown(units("^氵[뜻](물) + 皮[소리 ヒ](가죽 피)"), listOf(hun))
        assertEquals("", rest("물결.", "물결 파"))
        assertEquals("", rest("미워함.", "미워할 증"))
        assertEquals("", rest("", "물결 파"))
        // More than the meaning: it stays.
        assertEquals("본래 수소, 뒤에 특별하다.", rest("본래 수소, 뒤에 특별하다.", "특별할 특"))
        assertEquals("집 안에 인기척이 없어 고요함.", rest("집 안에 인기척이 없어 고요함.", "고요할 적"))
        assertEquals("침, 때림.", rest("침, 때림.", "칠 박"))
        // Two words: it says what the radical is doing there (糸 in 緑), which is worth the line.
        assertEquals("초록빛 비단.", rest("초록빛 비단.", "초록빛 록"))
        // A short phrase that is not the meaning stays too.
        assertEquals("쇠바늘.", rest("쇠바늘.", "물결 파"))
    }

    @Test
    fun findsTheFirstSentence() {
        assertEquals(9, Origin.sentenceEnd("가나다(라.마). 바사"))
        assertEquals(-1, Origin.sentenceEnd("마침표 없음"))
        assertEquals(3, Origin.sentenceEnd("가나."))
    }

    @Test
    fun aSoundPartShowsTheReadingTheKanjiHasNow() {
        // 特: 寺 gave ジ, the kanji is トク.
        assertEquals("トク", Kosei.soundNow("ジ", listOf("トク"), "トク"))
        // The same reading, or the same but for voicing: nothing to add.
        assertNull(Kosei.soundNow("セイ", listOf("セイ", "ショウ"), "セイ"))
        assertNull(Kosei.soundNow("ハン", listOf("バン", "ハン"), "バン"))
        assertNull(Kosei.soundNow("ハン", listOf("バン"), "バン"))
        // The reading the card lists first, else the dictionary's first.
        assertEquals("フウ", Kosei.soundNow("ハン", listOf("フ", "フウ"), "フウ"))
        assertEquals("フ", Kosei.soundNow("ハン", listOf("フ", "フウ"), null))
        // No on reading at all, or a part without a reading.
        assertNull(Kosei.soundNow("ジ", emptyList(), null))
        assertNull(Kosei.soundNow("", listOf("トク"), "トク"))
    }

    @Test
    fun theShippedNotesLoseOnlyWhatIsRepeated() {
        val kanji = asset("kanji.tsv").readLines().mapNotNull { Kanji.parse(it) }.associateBy { it.ch }
        var trimmed = 0
        var hidden = 0
        var total = 0
        for (line in asset("origin.tsv").readLines()) {
            if (line.isEmpty()) continue
            val ch = line.substringBefore('\t')
            val o = Origin.parse(line.substringAfter('\t')) ?: continue
            val k = kanji.getValue(ch)
            total++
            val units = o.kosei.takeIf { it.isNotEmpty() && it != "-" }?.let(Kosei::parse)
            val shown = o.shown(units, listOf(k.hunJa, k.hun, k.gloss))
            if (shown.isEmpty()) hidden++ else if (shown != o.body) trimmed++
            // What is shown is always the end of the note, never a rewording.
            assertTrue(ch, o.body.endsWith(shown))
            // A note about a change of shape or of meaning is never dropped.
            if (shown.isEmpty()) {
                assertFalse("$ch: ${o.body}", listOf("본래", "뒤에", "원래", "줄여", "의견").any { it in o.body })
            }
        }
        assertEquals(2136, total)
        // Since the 2026-11-14 manuscript most notes no longer start with the sentence the 구성 line says (it was
        // dropped from the data), so few are left to trim on the card.
        assertTrue("trimmed $trimmed", trimmed in 40..1000)
        assertTrue("hidden $hidden", hidden in 0..100)
    }
}
