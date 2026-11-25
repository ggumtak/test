package com.ggumtak.hanja

import android.content.Context

/**
 * The Anki card code in two parts (2026-11-25). A note type's template gets only the loader
 * (assets/anki-loader.html), which never changes; the code itself (assets/anki-tap.js) goes into Anki's media
 * folder as _hanja-tap-[VERSION].js, and the loader runs the highest number it finds there.
 *
 * Why: AnkiDroid takes at most about 1MB per call from another app, so a template larger than about 500,000
 * characters can never be written by the app (the JLPT MAX 덱 note types are 728,000–909,000 characters: "응답 -1").
 * The user pastes the loader into such a template once by hand; from then on new code needs no template change.
 */
object AnkiCode {
    /** Raise with every change to anki-tap.js (and the [N] inside it): Anki stores different content under the same name as a renamed copy. */
    const val VERSION = 8

    const val MEDIA_NAME = "_hanja-tap-$VERSION"

    fun loader(ctx: Context): String = asset(ctx, "anki-loader.html")

    fun script(ctx: Context): ByteArray = ctx.assets.open("anki-tap.js").use { it.readBytes() }

    private fun asset(ctx: Context, name: String) = ctx.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
}
