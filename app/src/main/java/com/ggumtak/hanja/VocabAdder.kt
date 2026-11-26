package com.ggumtak.hanja

import android.content.Context
import android.os.SystemClock
import java.util.Collections

/**
 * Puts a word the user keeps forgetting into Anki: a note of the user's vocabulary note type (JLPT MAX덱 어휘) in
 * the 자주 까먹는 단어 deck — a copy of the deck's own note for that word (real audio and pitch accent) when it has
 * one, else written by Claude in the format of that type's existing notes, without audio — with only the
 * Japanese-to-Korean cards left active. The Anki app is the one chosen in settings (AnkiDroid or myAmgi,
 * [AnkiBridge.open]). Every call blocks (IPC to that app, a Claude request): background threads only.
 */
object VocabAdder {
    class Outcome(val added: Boolean, val message: String, val already: Boolean = false)

    private const val SAMPLE_SCAN = 60
    private const val MODEL = "claude-sonnet-5-5"
    private val busy = HashSet<String>()
    private val activeClients = Collections.synchronizedSet(HashSet<ClaudeClient>())

    @Volatile
    private var lastClaudeCallAt = 0L

    fun isBusy(key: String): Boolean = synchronized(busy) { key in busy }

    /** Cancels any in-flight Claude generation (e.g. when [VocabService.onTimeout] fires). */
    fun cancelActive() {
        val snapshot = synchronized(activeClients) { activeClients.toList().also { activeClients.clear() } }
        snapshot.forEach { it.cancel() }
    }

    /**
     * Whether [key] is currently present in the configured vocabulary deck; null when the Anki app or the vocabulary
     * configuration is unavailable or unreachable. Blocking IPC: background threads only.
     */
    fun inDeck(ctx: Context, key: String, reading: String = ""): Boolean? {
        if (key.isEmpty() || !AnkiBridge.installed(ctx) || !AnkiBridge.granted(ctx)) return null
        val v = Prefs.vocab(ctx) ?: return null
        return runCatching { checkInDeck(AnkiBridge.open(ctx), v, key, reading) }.getOrNull()
    }

    /** Finds the note type and the deck, reads example notes, and saves them; throws with a Korean message. */
    fun setUp(ctx: Context): Prefs.Vocab {
        val cr = AnkiBridge.open(ctx)
        val type = VocabNote.pickNoteType(AnkiBridge.noteTypeNames(cr)) { AnkiBridge.count(cr, "mid:$it") }
            ?: throw IllegalStateException("'어휘'가 포함된 노트 유형을 찾지 못했습니다.")
        val deck = VocabNote.pickDeck(AnkiBridge.decks(cr).map { it.second })
            ?: throw IllegalStateException("'어휘' 또는 '자주 까먹는 단어' 덱을 찾지 못했습니다.")
        val fields = AnkiBridge.fieldNames(cr, type.first)
        val ids = AnkiBridge.noteIds(cr, VocabNote.deckNotesScope(type.first), SAMPLE_SCAN)
            .ifEmpty { AnkiBridge.noteIds(cr, "mid:${type.first}", SAMPLE_SCAN) }
        val found = ids.mapNotNull { id -> runCatching { AnkiBridge.noteFields(cr, id) }.getOrNull() }
            .ifEmpty {
                runCatching {
                    AnkiBridge.notes(cr, VocabNote.deckNotesScope(type.first), SAMPLE_SCAN).map { it.second }
                        .ifEmpty { AnkiBridge.notes(cr, "mid:${type.first}", SAMPLE_SCAN).map { it.second } }
                }.getOrDefault(emptyList())
            }
            .filter { it.size == fields.size }
        val samples = VocabNote.pickSamples(found)
        if (samples.isEmpty()) {
            throw IllegalStateException("'${type.second}' 노트 유형의 카드를 불러오지 못했습니다. ${cr.target.label}를 한 번 연 뒤 다시 시도해 주세요.")
        }
        return Prefs.Vocab(type.first, type.second, deck, fields, samples).also { Prefs.setVocab(ctx, it) }
    }

    /** [form]/[reading]: the dictionary entry the card found, "" when none. [auto]: automatic 5th-lookup add. */
    fun add(
        ctx: Context,
        target: String,
        form: String,
        reading: String,
        sentence: String,
        auto: Boolean = false,
    ): Outcome {
        val key = VocabNote.key(form, target)
        if (key.isEmpty()) return Outcome(false, "추가할 단어가 없습니다.")
        synchronized(busy) { if (!busy.add(key)) return Outcome(false, "이미 추가하는 중입니다.") }
        val app = AnkiBridge.label(ctx)
        try {
            return run(ctx, key, target, form, reading, sentence, auto)
        } catch (e: SecurityException) {
            return Outcome(false, "${app} 접근 권한이 없습니다. 앱 설정의 '단어장 → 설정 확인'에서 권한을 허용해 주세요.")
        } catch (e: IllegalStateException) {
            return Outcome(false, e.message?.takeIf { it.any { c -> c in '가'..'힣' } }
                ?: "${app}에 연결할 수 없습니다. ${app}를 한 번 연 뒤 다시 시도해 주세요.")
        } catch (e: Exception) {
            return Outcome(false, "Anki에 추가하지 못했습니다. ${app}를 한 번 연 뒤 다시 시도해 주세요.")
        } finally {
            synchronized(busy) { busy.remove(key) }
        }
    }

    private fun run(
        ctx: Context,
        key: String,
        target: String,
        form: String,
        reading: String,
        sentence: String,
        auto: Boolean,
    ): Outcome {
        if (!AnkiBridge.installed(ctx)) return Outcome(false, "AnkiDroid나 myAmgi가 설치되어 있지 않습니다.")
        if (!AnkiBridge.granted(ctx)) {
            return Outcome(false, "앱 설정의 '단어장 → 설정 확인'에서 ${AnkiBridge.label(ctx)} 권한을 허용해 주세요.")
        }
        if (auto && Prefs.vocabAdded(ctx, key)) {
            return Outcome(false, "이미 단어장에 추가된 단어입니다: $key", already = true)
        }
        val cr = AnkiBridge.open(ctx)
        var v = Prefs.vocab(ctx) ?: setUp(ctx)
        // The deck publisher may have changed the note type's fields since the examples were read.
        // …or replaced the note type (a deleted one has no fields): the saved choice is made again.
        if (runCatching { AnkiBridge.fieldNames(cr, v.typeId) }.getOrNull() != v.fields) v = setUp(ctx)
        val deckName = v.deck.substringAfterLast("::")
        val inDeck = checkInDeck(cr, v, key, reading)
        if (inDeck) {
            Prefs.markVocabAdded(ctx, key)
            return Outcome(false, "'$deckName' 덱에 이미 있는 단어입니다: $key", already = true)
        }
        if (Prefs.vocabAdded(ctx, key)) Prefs.unmarkVocabAdded(ctx, key)
        // The deck has the word: its own note, with the real audio, pitch accent and examples, copied as it is.
        sameWord(cr, v, VocabNote.deckNotesScope(v.typeId), key, reading)?.let { original ->
            AnkiBridge.addNote(cr, v.typeId, original, VocabNote.TAGS, AnkiBridge.deckId(cr, v.deck), VocabNote::suspend)
            Prefs.markVocabAdded(ctx, key)
            return Outcome(true, "단어장에 추가됨: $key")
        }
        val apiKey = Prefs.apiKey(ctx)
        if (apiKey.isEmpty()) return Outcome(false, "카드를 생성하려면 앱 설정에서 Claude API 키를 등록해 주세요.")
        // Quality over cost (the user's choice): Sonnet, thinking at medium effort. About 85원 a word, and only
        // for words the deck does not have; those it has are copied above for free.
        val client = ClaudeClient(apiKey, MODEL)
        activeClients.add(client)
        val (json, problem) = try {
            lastClaudeCallAt = SystemClock.elapsedRealtime()
            client.json(
                VocabNote.systemPrompt(v.fields, v.samples),
                VocabNote.userPrompt(target, form, reading, sentence, kanjiFacts(ctx, form.ifEmpty { target })),
                VocabNote.schema(v.fields), maxTokens = 16_000, effort = "medium", waitMs = 180_000,
            )
        } finally {
            activeClients.remove(client)
        }
        if (client.cancelled) return Outcome(false, "단어장 추가 시간이 초과되어 취소되었습니다.")
        val rawValues = json?.let { VocabNote.parse(it, v.fields, target) }
            ?: return Outcome(false, problem ?: "카드를 생성하지 못했습니다. 다시 시도해 주세요.")
        val values = if (rawValues.isNotEmpty() && rawValues[0].isBlank() && VocabNote.idField(v.fields[0])) {
            rawValues.toMutableList().apply { this[0] = "hanja-$key" }
        } else {
            rawValues
        }
        val deckId = AnkiBridge.deckId(cr, v.deck)
        AnkiBridge.addNote(cr, v.typeId, values, VocabNote.TAGS, deckId, VocabNote::suspend)
        Prefs.markVocabAdded(ctx, key)
        return Outcome(true, "단어장에 추가됨: $key (음성 없음)")
    }

    private const val SAME_WORD_SCAN = 500

    private fun checkInDeck(cr: AnkiBridge.Anki, v: Prefs.Vocab, key: String, reading: String): Boolean =
        if (VocabNote.wordField(v.fields) >= 0) {
            sameWord(cr, v, VocabNote.deckScope(v.deck), key, reading) != null
        } else {
            AnkiBridge.notes(cr, VocabNote.searchQuery(v.deck, key), 1).isNotEmpty()
        }

    private fun sameWord(cr: AnkiBridge.Anki, v: Prefs.Vocab, scope: String, key: String, reading: String): List<String>? {
        val q = VocabNote.sameWordQuery(scope, v.fields, key) ?: return null
        val rows = try {
            AnkiBridge.notes(cr, q, SAME_WORD_SCAN).map { it.second }
        } catch (e: SecurityException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            AnkiBridge.noteIds(cr, q, SAME_WORD_SCAN).mapNotNull { id ->
                runCatching { AnkiBridge.noteFields(cr, id) }.getOrNull()
            }
        }
        return VocabNote.sameWord(rows, v.fields, key, reading)
    }

    /** The app's own data on the word's kanji, so Claude does not guess 훈음, readings or strokes. */
    private fun kanjiFacts(ctx: Context, word: String): String = runCatching {
        KanjiStore.load(ctx)
        VocabNote.kanjiFacts(KanjiText.kanjiIn(word).mapNotNull { KanjiStore.get(it) })
    }.getOrDefault("")
}
