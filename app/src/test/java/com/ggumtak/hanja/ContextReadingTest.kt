package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextReadingTest {
    @Test
    fun parsesASplitReading() {
        val cr = ContextReading.parse("""{"word":"学校","reading":"がっこう","whole":false,
            "kanji":[{"char":"学","reading":"がっ"},{"char":"校","reading":"こう"}]}""", "学校")!!
        assertEquals("がっこう", cr.reading)
        assertFalse(cr.whole)
        assertEquals(mapOf("学" to "がっ", "校" to "こう"), cr.kanji)
    }

    @Test
    fun keepsEachKanjisMeaningEvenForWholeReadings() {
        val cr = ContextReading.parse("""{"word":"台所","reading":"だいどころ","whole":false,
            "kanji":[{"char":"台","reading":"だい","meaning":"받침대"},{"char":"所","reading":"どころ","meaning":"곳"}]}""", "台所")!!
        assertEquals(mapOf("台" to "받침대", "所" to "곳"), cr.meanings)
        val whole = ContextReading.parse("""{"word":"今日","reading":"きょう","whole":true,
            "kanji":[{"char":"今","reading":"","meaning":"지금"},{"char":"日","reading":"","meaning":"날"}]}""", "今日")!!
        assertTrue(whole.kanji.isEmpty())
        assertEquals("지금", whole.meanings["今"])
    }

    @Test
    fun wholeReadingsDropTheParts() {
        val cr = ContextReading.parse("""{"word":"今日","reading":"きょう","whole":true,
            "kanji":[{"char":"今","reading":""},{"char":"日","reading":""}]}""", "今日")!!
        assertTrue(cr.whole)
        assertTrue(cr.kanji.isEmpty())
    }

    @Test
    fun acceptsKatakanaAndAnInflectedWordAroundATappedKanji() {
        val cr = ContextReading.parse("""{"word":"暖かく","reading":"アタタカク","whole":false,
            "kanji":[{"char":"暖","reading":"あたた"}]}""", "暖")!!
        assertEquals("あたたかく", cr.reading)
        assertEquals(mapOf("暖" to "あたた"), cr.kanji)
    }

    @Test
    fun rejectsUnusableAnswers() {
        assertNull(ContextReading.parse("not json", "今日"))
        assertNull(ContextReading.parse("""{"word":"今日","reading":"kyou","whole":true,"kanji":[]}""", "今日"))
        assertNull(ContextReading.parse("""{"word":"雨","reading":"あめ","whole":false,"kanji":[]}""", "今日"))
        // A part that is not kana is dropped, the rest kept.
        val cr = ContextReading.parse("""{"word":"電気","reading":"でんき","whole":false,
            "kanji":[{"char":"電","reading":"den"},{"char":"気","reading":"き"}]}""", "電気")!!
        assertEquals(mapOf("気" to "き"), cr.kanji)
    }

    @Test
    fun schemaIsClosedAndComplete() {
        val s = ContextReading.SCHEMA
        assertEquals(false, s.get("additionalProperties"))
        assertEquals(4, s.getJSONArray("required").length())
        val item = s.getJSONObject("properties").getJSONObject("kanji").getJSONObject("items")
        assertEquals(false, item.get("additionalProperties"))
        assertEquals(3, item.getJSONArray("required").length())
    }

    @Test
    fun readsANonStreamedMessage() {
        val ok = ClaudeStream.messageText("""{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"a\":1}"}],"stop_reason":"end_turn"}""")
        assertEquals("{\"a\":1}", ok.first)
        assertNull(ok.second)
        assertTrue(ClaudeStream.messageText("""{"content":[],"stop_reason":"refusal"}""").second!!.contains("답하지"))
        assertNull(ClaudeStream.messageText("oops").first)
    }

    @Test
    fun promptFallsBackToTheSelection() {
        assertEquals("문장: 今日は雨\n찾을 글자: 今日", ContextReading.userPrompt("今日", "今日は雨"))
        assertEquals("문장: 電気をつけて\n찾을 글자: 電気をつけて", ContextReading.userPrompt("電気をつけて", ""))
    }
}
