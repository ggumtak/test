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

    /** v3: each kanji's meaning comes as a 훈음 (곳 소); older answers are asked again. */
    private fun cacheKey(target: String, sentence: String) = "v3\u0001" + target + "\u0001" + sentence

    /** Claude's earlier answer for this word in this sentence (cards come back every review). */
    fun cachedReading(ctx: Context, target: String, sentence: String): String? =
        ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).getString(cacheKey(target, sentence), null)

    fun cacheReading(ctx: Context, target: String, sentence: String, json: String) {
        val p = ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val e = p.edit()
        if (p.all.size >= CACHE_MAX) e.clear()
        e.putString(cacheKey(target, sentence), json).apply()
    }

    /** "sk-ant-…abcd" for showing which key is saved without showing it. */
    fun masked(key: String): String = if (key.length <= 12) "…" else key.take(7) + "…" + key.takeLast(4)
}
