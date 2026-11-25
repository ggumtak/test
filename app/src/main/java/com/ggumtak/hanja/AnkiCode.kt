package com.ggumtak.hanja

import android.content.Context
import java.util.Base64

/**
 * The Anki card code in two parts (2026-11-25). A note type's template gets only the loader
 * (assets/anki-loader.html), which never changes; the code itself (assets/anki-tap.js) goes into the note type's
 * CSS as one variable (--hjtap-code, Base64), which the loader reads and runs.
 *
 * Why: AnkiDroid takes at most about 1MB per call from another app, so a template larger than about 500,000
 * characters can never be written by the app (the JLPT MAX 덱 note types are 728,000–909,000 characters: "응답 -1").
 * The user pastes the loader into such a template once; from then on new code changes only the CSS, which is small.
 * Not a media file: AnkiDroid 2.22 stores a file sent by another app under a random name (_hanja-tap-8_60756….js,
 * 2026-11-25 사용자 화면), so a loader could never find it.
 */
object AnkiCode {
    fun loader(ctx: Context): String = ctx.assets.open("anki-loader.html").use { it.readBytes().toString(Charsets.UTF_8) }

    fun script(ctx: Context): ByteArray = ctx.assets.open("anki-tap.js").use { it.readBytes() }

    fun cssBlock(ctx: Context): String = AnkiCss.block(script(ctx))
}

/** The code's block at the end of a note type's CSS: found, replaced or removed (pure, tested in AnkiCssTest). */
object AnkiCss {
    const val START = "/* 한자 바로보기 코드:"
    const val END = "/* 한자 바로보기 코드 끝 */"

    fun block(script: ByteArray): String =
        "$START 한자 바로보기 앱이 넣고 바꿉니다. 고치지 마세요. */\n" +
            ":root{--hjtap-code:\"" + Base64.getEncoder().encodeToString(script) + "\";}\n" + END

    /** [css] without the block; a block cut off before its end is removed to the end. */
    fun strip(css: String): String {
        var out = css
        while (true) {
            val s = out.indexOf(START)
            if (s < 0) return out.trimEnd()
            val e = out.indexOf(END, s)
            val after = if (e < 0) "" else out.substring(e + END.length)
            out = out.substring(0, s).trimEnd() + (if (after.isBlank()) "" else "\n" + after.trimStart())
        }
    }

    private fun norm(s: String): String = if ('\r' in s) s.replace("\r\n", "\n") else s

    fun isCurrent(css: String, block: String): Boolean =
        css.indexOf(START) == css.lastIndexOf(START) && norm(css).contains(norm(block))

    fun install(css: String, block: String): String {
        val base = strip(css)
        return (if (base.isEmpty()) "" else base + "\n\n") + block + "\n"
    }
}
