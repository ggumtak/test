package com.ggumtak.hanja

import android.content.Context
import java.io.File

/** The settings and the Claude API key, kept only in this app's private storage on the phone. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY = "api_key"

    private fun settings(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(ctx: Context): String = settings(ctx).getString(KEY, "").orEmpty()

    fun setApiKey(ctx: Context, value: String) {
        settings(ctx).edit().putString(KEY, value.trim()).apply()
    }

    private const val AUTO_CONTEXT = "auto_context"
    private const val CHAT_MODEL = "chat_model"

    /** The model for 질문, Claude 설명 and 예문; Sonnet 5.5 unless changed. */
    fun chatModel(ctx: Context): String {
        val m = settings(ctx).getString(CHAT_MODEL, null)
        return m?.takeIf { id -> ClaudeClient.CHAT_MODELS.any { it.first == id } } ?: "claude-sonnet-5-5"
    }

    fun setChatModel(ctx: Context, model: String) {
        settings(ctx).edit().putString(CHAT_MODEL, model).apply()
    }

    private const val CHAT_EFFORT = "chat_effort"
    val EFFORTS = listOf("low" to "낮음 — 가장 빠름", "medium" to "중간", "high" to "높음 — 꼼꼼, 느림")

    /**
     * How hard the chat model thinks for 질문 and Claude 설명; 낮음 unless changed: no thinking step where the
     * model allows it, so the answer starts at once (ClaudeRequest.thinking).
     */
    fun chatEffort(ctx: Context): String {
        val e = settings(ctx).getString(CHAT_EFFORT, null)
        return e?.takeIf { v -> EFFORTS.any { it.first == v } } ?: "low"
    }

    fun setChatEffort(ctx: Context, effort: String) {
        settings(ctx).edit().putString(CHAT_EFFORT, effort).apply()
    }

    /** Ask Claude how the word is read in its sentence whenever a card opens (needs the API key). */
    fun autoContext(ctx: Context): Boolean = settings(ctx).getBoolean(AUTO_CONTEXT, true)

    fun setAutoContext(ctx: Context, on: Boolean) {
        settings(ctx).edit().putBoolean(AUTO_CONTEXT, on).apply()
    }

    private const val TAP_TYPES = "anki_tap_types"

    /** The note types the user put the Anki code in (AnkiTemplate.keep): it goes back in if a deck update removes it. */
    fun ankiTapTypes(ctx: Context): Set<Long> =
        settings(ctx).getStringSet(TAP_TYPES, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setAnkiTapTypes(ctx: Context, ids: Set<Long>) {
        settings(ctx).edit().putStringSet(TAP_TYPES, ids.map { it.toString() }.toSet()).apply()
    }

    // 자주 까먹는 단어 (VocabAdder): where words go, the examples Claude copies, what was added, lookup counts.

    class Vocab(val typeId: Long, val typeName: String, val deck: String, val fields: List<String>, val samples: List<List<String>>)

    private const val VOCAB = "vocab"
    private const val VOCAB_AUTO = "vocab_auto"

    fun vocab(ctx: Context): Vocab? = runCatching {
        val o = org.json.JSONObject(settings(ctx).getString(VOCAB, null) ?: return null)
        fun list(a: org.json.JSONArray) = List(a.length()) { a.getString(it) }
        val samples = o.getJSONArray("samples")
        Vocab(o.getLong("typeId"), o.getString("typeName"), o.getString("deck"), list(o.getJSONArray("fields")),
            List(samples.length()) { list(samples.getJSONArray(it)) })
    }.getOrNull()

    fun setVocab(ctx: Context, v: Vocab) {
        val o = org.json.JSONObject().put("typeId", v.typeId).put("typeName", v.typeName).put("deck", v.deck)
            .put("fields", org.json.JSONArray(v.fields))
            .put("samples", org.json.JSONArray(v.samples.map { org.json.JSONArray(it) }))
        settings(ctx).edit().putString(VOCAB, o.toString()).apply()
    }

    /** Put a word in on its [VocabNote.AUTO_AFTER]th lookup; on unless turned off. */
    fun vocabAuto(ctx: Context): Boolean = settings(ctx).getBoolean(VOCAB_AUTO, true)

    fun setVocabAuto(ctx: Context, on: Boolean) {
        settings(ctx).edit().putBoolean(VOCAB_AUTO, on).apply()
    }

    private fun vocabWords(ctx: Context) = ctx.getSharedPreferences("vocab_words", Context.MODE_PRIVATE)

    fun vocabAdded(ctx: Context, key: String): Boolean = vocabWords(ctx).getBoolean("added:$key", false)

    fun markVocabAdded(ctx: Context, key: String) {
        vocabWords(ctx).edit().putBoolean("added:$key", true).apply()
    }

    /** One more lookup of [key] (at most one per 10 minutes); the count so far. */
    @Synchronized
    fun countLookup(ctx: Context, key: String): Int {
        val p = vocabWords(ctx)
        val (n, stored) = VocabNote.count(p.getString("count:$key", null), System.currentTimeMillis())
        p.edit().putString("count:$key", stored).apply()
        return n
    }

    private const val SHOW_HANGUL = "show_hangul"

    /** The Hangul pronunciation after kana on the card and in the AI answers; off unless turned on. */
    fun showHangul(ctx: Context): Boolean = settings(ctx).getBoolean(SHOW_HANGUL, false)

    fun setShowHangul(ctx: Context, on: Boolean) {
        settings(ctx).edit().putBoolean(SHOW_HANGUL, on).apply()
    }

    /** "sk-ant-…abcd" for showing which key is saved without showing it. */
    fun masked(key: String): String = if (key.length <= 12) "…" else key.take(7) + "…" + key.takeLast(4)

    // Saved answers: one file each under files/answers (AnswerStore). Called on background threads only.

    private const val READINGS = "context_readings"
    private const val EXAMPLES = "examples"
    private const val CACHE_MAX = 3000
    private val stores = HashMap<String, AnswerStore>()

    /** v5 discards answers that predate sentence/split validation. */
    fun cachedReading(ctx: Context, target: String, sentence: String): String? =
        store(ctx, READINGS).get(AnswerCache.key("reading-v5", target, sentence))

    fun cacheReading(ctx: Context, target: String, sentence: String, json: String) =
        store(ctx, READINGS).put(AnswerCache.key("reading-v5", target, sentence), json)

    fun cachedExamples(ctx: Context, word: String, sentence: String): String? =
        store(ctx, EXAMPLES).get(AnswerCache.key("examples-v3", word, sentence))

    fun cacheExamples(ctx: Context, word: String, sentence: String, json: String) =
        store(ctx, EXAMPLES).put(AnswerCache.key("examples-v3", word, sentence), json)

    /** False until the first use after an update has moved earlier builds' saved readings (slow, once). */
    @Synchronized
    fun answersReady(ctx: Context): Boolean =
        READINGS in stores || !File(ctx.applicationContext.dataDir, "shared_prefs/$READINGS.xml").exists()

    /**
     * The store for [name]; the first use after an update moves the answers saved by earlier builds out of the
     * SharedPreferences file of the same name (same keys), then deletes that file.
     */
    @Synchronized
    private fun store(ctx: Context, name: String): AnswerStore = stores.getOrPut(name) {
        val app = ctx.applicationContext
        val store = AnswerStore(File(app.filesDir, "answers/$name"), CACHE_MAX)
        if (File(app.dataDir, "shared_prefs/$name.xml").exists()) {
            runCatching {
                for ((key, value) in app.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                    AnswerCache.legacy(value)?.let { (answer, time) -> store.put(key, answer, time, evict = false) }
                }
                store.trim()
            }
            app.deleteSharedPreferences(name)
        }
        store
    }
}
