package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleTest {
    @Test
    fun parsesExamples() {
        val list = Example.parse("""{"examples":[
            {"ja":"部屋をきれいに掃除しました。","word":"部屋","reading":"へやお きれいに そうじしました。","ko":"방을 깨끗하게 청소했습니다."},
            {"ja":"この部屋は明るいです。","word":"部屋","reading":"この へやわ あかるいです。","ko":"이 방은 밝습니다."}]}""")
        assertEquals(2, list.size)
        assertEquals("部屋", list[0].word)
        assertEquals("헤야오 키레이니 소우지시마시타。", KanaHangul.convert(list[0].reading))
        assertEquals("코노 헤야와 아카루이데스。", KanaHangul.convert(list[1].reading))
    }

    @Test
    fun dropsBadPartsAndBadExamples() {
        val list = Example.parse("""{"examples":[
            {"ja":"貼ってあります。","word":"貼る","reading":"hatte arimasu","ko":"붙어 있습니다."},
            {"ja":"No Japanese here","word":"x","reading":"","ko":"없음"},
            {"ja":"雨です。","word":"雨","reading":"アメデス。","ko":""},
            {"ja":"一","word":"一","reading":"いち","ko":"1"},
            {"ja":"二","word":"二","reading":"に","ko":"2"},
            {"ja":"三","word":"三","reading":"さん","ko":"3"}]}""")
        // A word not in the sentence and a non-kana reading are blanked; no Japanese or no Korean drops it; at most 3.
        assertEquals(listOf("貼ってあります。", "一", "二"), list.map { it.ja })
        assertEquals("", list[0].word)
        assertEquals("", list[0].reading)
        assertTrue(Example.parse("not json").isEmpty())
    }

    @Test
    fun schemaIsClosed() {
        val s = Example.SCHEMA
        assertEquals(false, s.get("additionalProperties"))
        val item = s.getJSONObject("properties").getJSONObject("examples").getJSONObject("items")
        assertEquals(false, item.get("additionalProperties"))
        assertEquals(4, item.getJSONArray("required").length())
        assertEquals("단어: 部屋\n원래 문장: 部屋は暗い", Example.userPrompt("部屋", "部屋は暗い"))
        assertEquals("단어: 部屋", Example.userPrompt("部屋", ""))
    }
}
