package com.ggumtak.hanja

import android.content.Context

/** The Claude API key, kept only in this app's private storage on the phone. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY = "api_key"

    fun apiKey(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun setApiKey(ctx: Context, value: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, value.trim()).apply()
    }

    private const val AUTO_CONTEXT = "auto_context"
    private const val CHAT_MODEL = "chat_model"

    /** The model for 질문 and Claude 설명; Sonnet 5.5 unless changed. */
    fun chatModel(ctx: Context): String {
        val m = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHAT_MODEL, null)
        return m?.takeIf { id -> ClaudeClient.CHAT_MODELS.any { it.first == id } } ?: "claude-sonnet-5-5"
    }

    private const val CHAT_EFFORT = "chat_effort"
    val EFFORTS = listOf("low" to "낮음 — 빠름", "medium" to "중간", "high" to "높음 — 꼼꼼, 조금 느림")

    /** How hard the chat model thinks for 질문 and Claude 설명; 중간 unless changed. */
    fun chatEffort(ctx: Context): String {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHAT_EFFORT, null)
        return e?.takeIf { v -> EFFORTS.any { it.first == v } } ?: "medium"
    }

    fun setChatEffort(ctx: Context, effort: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHAT_EFFORT, effort).apply()
    }

    fun setChatModel(ctx: Context, model: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHAT_MODEL, model).apply()
    }
    private const val CACHE = "context_readings"
    private const val CACHE_MAX = 3000

    /** Ask Claude how the word is read in its sentence whenever a card opens (needs the API key). */
    fun autoContext(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(AUTO_CONTEXT, true)

    fun setAutoContext(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(AUTO_CONTEXT, on).apply()
    }

    /** v5 discards answers that predate sentence/split validation. Keys never contain card text. */
    fun cachedReading(ctx: Context, target: String, sentence: String): String? =
        cached(ctx, CACHE, AnswerCache.key("reading-v5", target, sentence))

    fun cacheReading(ctx: Context, target: String, sentence: String, json: String) =
        cache(ctx, CACHE, AnswerCache.key("reading-v5", target, sentence), json)

    private const val EXAMPLES = "examples"

    fun cachedExamples(ctx: Context, word: String, sentence: String): String? =
        cached(ctx, EXAMPLES, AnswerCache.key("examples-v3", word, sentence))

    fun cacheExamples(ctx: Context, word: String, sentence: String, json: String) =
        cache(ctx, EXAMPLES, AnswerCache.key("examples-v3", word, sentence), json)

    private fun cached(ctx: Context, file: String, key: String): String? =
        ctx.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, null)?.let(AnswerCache::answer)

    /** Called on workers. At capacity evict only the oldest tenth, keeping most reviewed cards warm. */
    @Synchronized
    private fun cache(ctx: Context, file: String, key: String, json: String) {
        val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        val e = p.edit()
        if (!p.contains(key)) AnswerCache.evictions(p.all, CACHE_MAX).forEach { e.remove(it) }
        e.putString(key, AnswerCache.pack(json)).apply()
    }

    /** "sk-ant-…abcd" for showing which key is saved without showing it. */
    fun masked(key: String): String = if (key.length <= 12) "…" else key.take(7) + "…" + key.takeLast(4)
}
