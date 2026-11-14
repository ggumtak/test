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
        assertEquals("電気(でんき 덴키)をつけて、今日(きょう 쿄-)は", d.text)
        assertEquals(listOf("(でんき 덴키)", "(きょう 쿄-)"), marked(d, Rich.Kind.READING))
        assertEquals(listOf("덴키", "쿄-"), marked(d, Rich.Kind.HANGUL))
    }

    @Test
    fun readingsAfterOkuriganaToo() {
        assertEquals("食べる(たべる 타베루)", Rich.parse("食べる(たべる)").text)
        assertEquals("貼って(はって 핫테)ある", Rich.parse("貼って(はって)ある").text)
        assertEquals("食(た 타)べる", Rich.parse("食(た)べる").text)
        // A long vowel is "-" inside the kanji's reading, never across the okurigana: 思う says its う.
        assertEquals("学校(がっこう 갓코-)", Rich.parse("学校(がっこう)").text)
        assertEquals("思う(おもう 오모우)", Rich.parse("思う(おもう)").text)
        assertEquals("思(おも 오모)う", Rich.parse("思(おも)う").text)
        assertEquals("大きい(おおきい 오-키-)", Rich.parse("大きい(おおきい)").text)
        assertEquals(listOf("(たべる 타베루)"), marked(Rich.parse("食べる(たべる)"), Rich.Kind.READING))
        // More than six kana between the kanji and the parenthesis: not a reading of that word.
        val far = "気をつけてくださいね(ね)"
        assertEquals(far, Rich.parse(far).text)
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
    fun theLineThatAddsUpAWord() {
        // 【글자 풀이】: one bullet per kanji, then "= …" putting them together, indented like the bullets.
        val d = Rich.parse("【글자 풀이】\n- 仕(し): する의 し\n= し + 事(こと) → '하는 일'\na = b")
        assertEquals("【글자 풀이】\n• 仕(し 시): する의 し\n= し + 事(こと 코토) → '하는 일'\na = b", d.text)
        assertEquals(listOf("="), marked(d, Rich.Kind.SUM))
        assertEquals(listOf("• 仕(し 시): する의 し", "= し + 事(こと 코토) → '하는 일'"), marked(d, Rich.Kind.BULLET))
        assertTrue(marked(d, Rich.Kind.NOTE).isEmpty())
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

    @Test
    fun theExplanationsBulletsSubHeadingsAndMemoryLine() {
        val src = "【글자 결합 & 연상】\n・名(な): 이름\n・ 前(まえ): 앞\n💡 연상: 앞(前)에 내건 이름표(名) → 이름!\n" +
            "【한자별 가지치기】\n① 名 (이름 명)\n・음독(メイ・ミョウ): 有名(ゆうめい) 유명"
        val d = Rich.parse(src, hangul = false)
        assertEquals("【글자 결합 & 연상】\n• 名(な): 이름\n• 前(まえ): 앞\n💡 연상: 앞(前)에 내건 이름표(名) → 이름!\n" +
            "【한자별 가지치기】\n① 名 (이름 명)\n• 음독(メイ・ミョウ): 有名(ゆうめい) 유명", d.text)
        assertEquals(listOf("【글자 결합 & 연상】", "【한자별 가지치기】"), marked(d, Rich.Kind.LABEL))
        assertEquals(listOf("① 名 (이름 명)"), marked(d, Rich.Kind.HEAD))
        assertEquals(listOf("💡 연상:"), marked(d, Rich.Kind.TIP))
        assertEquals(4, marked(d, Rich.Kind.BULLET).size)
        // Without Hangul the readings stay as written but are still marked (small and grey on the card).
        assertTrue(marked(d, Rich.Kind.HANGUL).isEmpty())
        assertEquals(listOf("(な)", "(まえ)", "(ゆうめい)"), marked(d, Rich.Kind.READING))
        // 名 (이름 명): Hangul in parentheses is no reading. A 가운뎃점 inside a line is no bullet.
        assertEquals("メイ・ミョウ", Rich.parse("メイ・ミョウ").text)
    }
}
