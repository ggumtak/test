package com.ggumtak.hanja

import android.content.Context

/**
 * Puts a word the user keeps forgetting into Anki: a note of the user's vocabulary note type (JLPT MAX덱 어휘) in
 * the 자주 까먹는 단어 deck — a copy of the deck's own note for that word (real audio and pitch accent) when it has
 * one, else written by Claude in the format of that type's existing notes, without audio — with only the
 * Japanese-to-Korean cards left active. Every call blocks (AnkiDroid IPC, a Claude request): background threads only.
 */
object VocabAdder {
    class Outcome(val added: Boolean, val message: String, val already: Boolean = false)

    private const val SAMPLE_SCAN = 300
    private const val MODEL = "claude-sonnet-5-5"
    private val busy = HashSet<String>()

    /** Finds the note type and the deck, reads example notes, and saves them; throws with a Korean message. */
    fun setUp(ctx: Context): Prefs.Vocab {
        val cr = ctx.contentResolver
        val type = VocabNote.pickNoteType(AnkiBridge.noteTypeNames(cr)) { AnkiBridge.count(cr, "mid:$it") }
            ?: throw IllegalStateException("이름에 '어휘'가 들어가고 카드가 있는 노트 유형을 찾지 못했습니다.")
        val deck = VocabNote.pickDeck(AnkiBridge.decks(cr).map { it.second })
            ?: throw IllegalStateException("'어휘' 덱이나 '자주 까먹는 단어' 덱을 찾지 못했습니다.")
        val fields = AnkiBridge.fieldNames(cr, type.first)
        val found = AnkiBridge.notes(cr, VocabNote.deckNotesScope(type.first), SAMPLE_SCAN).map { it.second }
            .ifEmpty { AnkiBridge.notes(cr, "mid:${type.first}", SAMPLE_SCAN).map { it.second } }
            .filter { it.size == fields.size }
        val samples = VocabNote.pickSamples(found)
        if (samples.isEmpty()) {
            throw IllegalStateException("'${type.second}' 노트 유형의 카드를 읽지 못했습니다. AnkiDroid를 한 번 열고 다시 눌러 주세요.")
        }
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
        // …or replaced the note type (a deleted one has no fields): the saved choice is made again.
        if (runCatching { AnkiBridge.fieldNames(cr, v.typeId) }.getOrNull() != v.fields) v = setUp(ctx)
        val deckName = v.deck.substringAfterLast("::")
        val inDeck = AnkiBridge.notes(cr, VocabNote.searchQuery(v.deck, key), 1).isNotEmpty() ||
            sameWord(cr, v, VocabNote.deckScope(v.deck), key, reading) != null
        if (inDeck) {
            Prefs.markVocabAdded(ctx, key)
            return Outcome(false, "'$key'는 이미 '$deckName'에 있습니다.", already = true)
        }
        // The deck has the word: its own note, with the real audio, pitch accent and examples, copied as it is.
        sameWord(cr, v, VocabNote.deckNotesScope(v.typeId), key, reading)?.let { original ->
            AnkiBridge.addNote(cr, v.typeId, original, VocabNote.TAGS, AnkiBridge.deckId(cr, v.deck), VocabNote::suspend)
            Prefs.markVocabAdded(ctx, key)
            return Outcome(true, "'$key'를 덱의 원래 카드 그대로 '$deckName'에 넣었습니다.")
        }
        val apiKey = Prefs.apiKey(ctx)
        if (apiKey.isEmpty()) return Outcome(false, "카드 내용을 채우려면 앱 첫 화면에 Claude API 키를 저장해 주세요.")
        // Quality over cost (the user's choice): Sonnet, thinking at medium effort. About 85원 a word, and only
        // for words the deck does not have; those it has are copied above for free.
        val (json, problem) = ClaudeClient(apiKey, MODEL).json(VocabNote.systemPrompt(v.fields, v.samples),
            VocabNote.userPrompt(target, form, reading, sentence, kanjiFacts(ctx, form.ifEmpty { target })),
            VocabNote.schema(v.fields), maxTokens = 16_000, effort = "medium", waitMs = 180_000)
        val values = json?.let { VocabNote.parse(it, v.fields, target) }
            ?: return Outcome(false, problem ?: "Claude가 카드 내용을 제대로 만들지 못했습니다. 다시 눌러 주세요.")
        val deckId = AnkiBridge.deckId(cr, v.deck)
        AnkiBridge.addNote(cr, v.typeId, values, VocabNote.TAGS, deckId, VocabNote::suspend)
        Prefs.markVocabAdded(ctx, key)
        return Outcome(true, "'$key'를 '$deckName'에 넣었습니다. 덱에 없는 단어라 음성은 없습니다.")
    }

    private const val SAME_WORD_SCAN = 500

    private fun sameWord(cr: android.content.ContentResolver, v: Prefs.Vocab, scope: String, key: String, reading: String): List<String>? {
        val q = VocabNote.sameWordQuery(scope, v.fields, key) ?: return null
        return VocabNote.sameWord(AnkiBridge.notes(cr, q, SAME_WORD_SCAN).map { it.second }, v.fields, key, reading)
    }

    /** The app's own data on the word's kanji, so Claude does not guess 훈음, readings or strokes. */
    private fun kanjiFacts(ctx: Context, word: String): String = runCatching {
        KanjiStore.load(ctx)
        VocabNote.kanjiFacts(KanjiText.kanjiIn(word).mapNotNull { KanjiStore.get(it) })
    }.getOrDefault("")
}
