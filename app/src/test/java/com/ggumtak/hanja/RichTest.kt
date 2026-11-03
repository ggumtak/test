package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTest {
    private fun marked(doc: Rich.Doc, kind: Rich.Kind) =
        doc.marks.filter { it.kind == kind }.map { doc.text.substring(it.start, it.end) }

    @Test
    fun readingsGetTheHangulPronunciation() {
        val d = Rich.parse("電気(でんき)をつけて、今日（きょう）は")
        assertEquals("電気(でんき 덴키)をつけて、今日(きょう 쿄우)は", d.text)
        assertEquals(listOf("(でんき 덴키)", "(きょう 쿄우)"), marked(d, Rich.Kind.READING))
        assertEquals(listOf("덴키", "쿄우"), marked(d, Rich.Kind.HANGUL))
    }

    @Test
    fun leavesOtherParenthesesAlone() {
        // Not after a kanji, already with Hangul, or not kana: unchanged.
        val s = "たべる(먹다) 学校(がっこう, 각코우) 気(氣) 3(さん)"
        assertEquals(s, Rich.parse(s).text)
        assertTrue(Rich.parse(s).marks.isEmpty())
    }

    @Test
    fun labelsBulletsNotesAndBold() {
        val d = Rich.parse("【뜻】 **전기**라는 뜻이에요.\n\n\n\n- 電車(でんしゃ) 전철\n* 停電\n→ 불을 켜 줘.\n## 정리")
        assertEquals("【뜻】 전기라는 뜻이에요.\n\n• 電車(でんしゃ 덴샤) 전철\n• 停電\n→ 불을 켜 줘.\n정리", d.text)
        assertEquals(listOf("【뜻】", "정리"), marked(d, Rich.Kind.LABEL))
        assertEquals(listOf("전기"), marked(d, Rich.Kind.BOLD))
        assertEquals(listOf("• 電車(でんしゃ 덴샤) 전철", "• 停電"), marked(d, Rich.Kind.BULLET))
        assertEquals(listOf("→ 불을 켜 줘."), marked(d, Rich.Kind.NOTE))
    }

    @Test
    fun boldAtTheStartIsNotABulletAndHalfStreamedTextIsKept() {
        assertEquals("뜻: 빛", Rich.parse("**뜻**: 빛").text)
        assertEquals(listOf("뜻"), marked(Rich.parse("**뜻**: 빛"), Rich.Kind.BOLD))
        // Mid-stream: an unclosed bold or reading shows as it is until the rest arrives.
        assertEquals("**전", Rich.parse("**전").text)
        assertEquals("電気(でん", Rich.parse("電気(でん").text)
        assertEquals("", Rich.parse("").text)
    }
}
