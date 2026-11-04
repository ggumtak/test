package com.ggumtak.hanja

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabNoteTest {
    private val fields = listOf("Word", "Reading", "Meaning", "Sentence", "Audio", "KoreanRecallPrompt")

    @Test
    fun picksTheVocabularyNoteTypeAndDeck() {
        val types = listOf(1L to "Basic", 2L to "JLPT MAX덱 어휘문제", 3L to "JLPT MAX덱 어휘")
        assertEquals(3L, VocabNote.pickNoteType(types)!!.first)
        assertNull(VocabNote.pickNoteType(listOf(1L to "Basic")))
        // The deck the user made wins over a new one under 종합 실전::어휘.
        val decks = listOf("JLPT", "JLPT::종합 실전", "JLPT::종합 실전::어휘", "JLPT::어휘", "JLPT::어휘::자주 까먹는 단어")
        assertEquals("JLPT::어휘::자주 까먹는 단어", VocabNote.pickDeck(decks))
        assertEquals("JLPT::종합 실전::어휘::자주 까먹는 단어", VocabNote.pickDeck(decks.dropLast(1)))
        assertEquals("어휘::자주 까먹는 단어", VocabNote.pickDeck(listOf("기본", "어휘")))
        assertNull(VocabNote.pickDeck(listOf("기본")))
    }

    @Test
    fun examplesAreTheFullestNotesWithoutMedia() {
        val notes = listOf(
            listOf("", "", "", "", "", ""),
            listOf("日", "ひ", "", "", "", ""),
            listOf("<ruby>忘<rt>わす</rt></ruby>れる", "わすれる", "잊다", "傘を忘れた。", "[sound:w.mp3]", "<div>잊다</div>"),
        )
        val picked = VocabNote.pickSamples(notes)
        assertEquals(2, picked.size)
        assertEquals("わすれる", picked[0][1])
        val prompt = VocabNote.systemPrompt(fields, picked)
        assertFalse(prompt.contains("[sound:"))
        assertTrue(prompt.contains("<ruby>忘<rt>わす</rt></ruby>れる"))
        assertTrue(prompt.contains("\"KoreanRecallPrompt\""))
        // A second example that would make the prompt too long is left out.
        val big = listOf(List(6) { "あ".repeat(1500) }, List(6) { "い".repeat(1500) })
        val p = VocabNote.systemPrompt(fields, big)
        assertTrue(p.contains("あああ"))
        assertFalse(p.contains("いいい"))
    }

    @Test
    fun schemaRequiresEveryField() {
        val s = VocabNote.schema(fields)
        assertEquals(fields.size, s.getJSONArray("required").length())
        assertFalse(s.getBoolean("additionalProperties"))
        assertTrue(s.getJSONObject("properties").has("KoreanRecallPrompt"))
    }

    @Test
    fun answerIsCleanedAndChecked() {
        val o = JSONObject().put("Word", "<ruby>傘<rt>かさ</rt></ruby>").put("Reading", "かさ").put("Meaning", "우산")
            .put("Sentence", "傘を忘れた。<script>alert(1)</script>").put("Audio", "[sound:x.mp3]").put("KoreanRecallPrompt", "")
        val v = VocabNote.parse(o.toString(), fields, "傘")!!
        assertEquals("傘を忘れた。", v[3])
        assertEquals("", v[4])
        // A missing field, or a note that never mentions the word, is refused.
        assertNull(VocabNote.parse(JSONObject().put("Word", "傘").toString(), fields, "傘"))
        val other = JSONObject(o.toString()).put("Word", "雨").put("Reading", "あめ").put("Sentence", "雨です。")
        assertNull(VocabNote.parse(other.toString(), fields, "傘"))
        assertEquals("a b", VocabNote.joinFields(listOf("a", "b")).replace('\u001f', ' '))
        assertEquals(listOf("a", "b c"), VocabNote.splitFields(VocabNote.joinFields(listOf("a", "b\u001fc"))))
    }

    @Test
    fun onlyJapaneseToKoreanCardsStayActive() {
        assertFalse(VocabNote.suspend("어휘"))
        assertFalse(VocabNote.suspend("어휘(가나 보조)"))
        assertTrue(VocabNote.suspend("음성"))
        assertTrue(VocabNote.suspend("어휘(한국어→일본어)"))
    }

    @Test
    fun searchKeepsDeckHierarchyAndEscapesTheWord() {
        assertEquals("deck:\"어휘::자주 까먹는 단어\" \"忘れる\"", VocabNote.searchQuery("어휘::자주 까먹는 단어", "忘れる"))
        assertEquals("deck:\"a\" \"x\\:y\\*\"", VocabNote.searchQuery("a", "x:y*"))
    }

    @Test
    fun lookupsCountOncePerTenMinutes() {
        var stored: String? = null
        var t = 1_000_000L
        val counts = mutableListOf<Int>()
        repeat(3) {
            val (n, s) = VocabNote.count(stored, t)
            counts += n
            stored = s
            t += 60_000
        }
        assertEquals(listOf(1, 1, 1), counts)
        assertEquals(2, VocabNote.count(stored, t + 10 * 60_000L).first)
        assertEquals("忘れる", VocabNote.key("忘れる", "忘"))
        assertEquals("忘", VocabNote.key("", "忘"))
    }
}
