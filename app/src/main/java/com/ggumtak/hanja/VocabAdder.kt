package com.ggumtak.hanja

import android.content.Context

/**
 * Puts a word the user keeps forgetting into Anki: a note of the user's vocabulary note type (JLPT MAX덱 어휘),
 * written by Claude in the format of that type's existing notes, in the 자주 까먹는 단어 deck, with only the
 * Japanese-to-Korean cards left active. Every call blocks (AnkiDroid IPC, a Claude request): background threads only.
 */
object VocabAdder {
    class Outcome(val added: Boolean, val message: String, val already: Boolean = false)

    private const val SAMPLE_SCAN = 300
    private val busy = HashSet<String>()

    /** Finds the note type and the deck, reads example notes, and saves them; throws with a Korean message. */
    fun setUp(ctx: Context): Prefs.Vocab {
        val cr = ctx.contentResolver
        val type = VocabNote.pickNoteType(AnkiBridge.noteTypeNames(cr))
            ?: throw IllegalStateException("이름에 '어휘'가 들어간 노트 유형을 찾지 못했습니다.")
        val deck = VocabNote.pickDeck(AnkiBridge.decks(cr).map { it.second })
            ?: throw IllegalStateException("'어휘' 덱이나 '자주 까먹는 단어' 덱을 찾지 못했습니다.")
        val fields = AnkiBridge.fieldNames(cr, type.first)
        val found = AnkiBridge.notes(cr, "mid:${type.first} -tag:자주까먹는단어", SAMPLE_SCAN).map { it.second }
            .filter { it.size == fields.size }
        val samples = VocabNote.pickSamples(found)
        if (samples.isEmpty()) throw IllegalStateException("'${type.second}' 노트 유형에 본보기로 삼을 카드가 없습니다.")
        return Prefs.Vocab(type.first, type.second, deck, fields, samples).also { Prefs.setVocab(ctx, it) }
    }

    /** [form]/[reading]: the dictionary entry the card found, "" when none. */
    fun add(ctx: Context, target: String, form: String, reading: String, sentence: String): Outcome {
        val key = VocabNote.key(form, target)
        if (key.isEmpty()) return Outcome(false, "넣을 단어가 없습니다.")
        synchronized(busy) { if (!busy.add(key)) return Outcome(false, "넣는 중입니다.") }
        try {
            return run(ctx, key, target, form, reading, sentence)
        } catch (e: SecurityException) {
            return Outcome(false, "AnkiDroid 접근이 허용되지 않았습니다. 앱 첫 화면의 '단어장' 항목에서 '설정 확인'을 눌러 허용해 주세요.")
        } catch (e: IllegalStateException) {
            return Outcome(false, e.message?.takeIf { it.any { c -> c in '가'..'힣' } }
                ?: "AnkiDroid에 연결하지 못했습니다. AnkiDroid를 한 번 열고 다시 눌러 주세요.")
        } catch (e: Exception) {
            return Outcome(false, "Anki에 넣지 못했습니다. AnkiDroid를 한 번 열고 다시 눌러 주세요.")
        } finally {
            synchronized(busy) { busy.remove(key) }
        }
    }

    private fun run(ctx: Context, key: String, target: String, form: String, reading: String, sentence: String): Outcome {
        if (!AnkiBridge.installed(ctx)) return Outcome(false, "AnkiDroid가 설치되어 있지 않습니다.")
        if (!AnkiBridge.granted(ctx)) {
            return Outcome(false, "앱 첫 화면의 '단어장' 항목에서 '설정 확인'을 눌러 AnkiDroid 접근을 허용해 주세요.")
        }
        if (Prefs.vocabAdded(ctx, key)) return Outcome(false, "'$key'는 이미 넣은 단어입니다.", already = true)
        val cr = ctx.contentResolver
        var v = Prefs.vocab(ctx) ?: setUp(ctx)
        // The deck publisher may have changed the note type's fields since the examples were read.
        if (AnkiBridge.fieldNames(cr, v.typeId) != v.fields) v = setUp(ctx)
        if (AnkiBridge.notes(cr, VocabNote.searchQuery(v.deck, key), 1).isNotEmpty()) {
            Prefs.markVocabAdded(ctx, key)
            return Outcome(false, "'$key'는 이미 '${v.deck.substringAfterLast("::")}'에 있습니다.", already = true)
        }
        val apiKey = Prefs.apiKey(ctx)
        if (apiKey.isEmpty()) return Outcome(false, "카드 내용을 채우려면 앱 첫 화면에 Claude API 키를 저장해 주세요.")
        val (json, problem) = ClaudeClient(apiKey).json(VocabNote.systemPrompt(v.fields, v.samples),
            VocabNote.userPrompt(target, form, reading, sentence), VocabNote.schema(v.fields), maxTokens = 6000)
        val values = json?.let { VocabNote.parse(it, v.fields, target) }
            ?: return Outcome(false, problem ?: "Claude가 카드 내용을 제대로 만들지 못했습니다. 다시 눌러 주세요.")
        val deckId = AnkiBridge.deckId(cr, v.deck)
        AnkiBridge.addNote(cr, v.typeId, values, VocabNote.TAGS, deckId, VocabNote::suspend)
        Prefs.markVocabAdded(ctx, key)
        return Outcome(true, "'$key'를 '${v.deck.substringAfterLast("::")}'에 넣었습니다.")
    }
}
