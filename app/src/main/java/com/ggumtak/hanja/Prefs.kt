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

    /** "sk-ant-…abcd" for showing which key is saved without showing it. */
    fun masked(key: String): String = if (key.length <= 12) "…" else key.take(7) + "…" + key.takeLast(4)
}
