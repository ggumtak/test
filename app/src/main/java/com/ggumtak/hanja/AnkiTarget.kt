package com.ggumtak.hanja

/**
 * The Anki app the 단어장 and the card reading talk to (2026-11-25, 사용자 요청: "myAmgi에서도 AnkiDroid만큼").
 * All three answer AnkiDroid's public ContentProvider API (FlashCardsContract) at their own authority, behind
 * their own permission; only the address differs. myAmgi (the owner's own Anki app, built on the official Anki
 * engine) loads this app's card script (assets/anki-tap.js) by itself, so the template code is AnkiDroid's only.
 *
 * Pure (no Android classes): which app is used is decided by [choose], tested in AnkiTargetTest.
 */
enum class AnkiTarget(
    val pkg: String,
    val label: String,
    val authority: String,
    val permission: String,
    /** The app needs the card code put into its note types (AnkiDroid); myAmgi runs it natively. */
    val needsTemplateCode: Boolean,
) {
    ANKIDROID("com.ichi2.anki", "AnkiDroid", "com.ichi2.anki.flashcards",
        "com.ichi2.anki.permission.READ_WRITE_DATABASE", true),
    MYAMGI("com.ggumtak.myamgi", "myAmgi", "com.ggumtak.myamgi.flashcards",
        "com.ggumtak.myamgi.permission.READ_WRITE_DATABASE", false),
    MYAMGI_COMET("com.ggumtak.myamgi.comet", "myAmgi(코멧)", "com.ggumtak.myamgi.comet.flashcards",
        "com.ggumtak.myamgi.comet.permission.READ_WRITE_DATABASE", false);

    val isMyAmgi: Boolean get() = this != ANKIDROID

    /** [target]: null when none of the apps is installed; [note]: why it was picked, shown under the choice. */
    class Choice(val target: AnkiTarget?, val note: String, val chosenByUser: Boolean)

    companion object {
        /** When several are installed and the user has not chosen: myAmgi first (the owner studies there now). */
        private val PREFERENCE = listOf(MYAMGI, MYAMGI_COMET, ANKIDROID)

        fun of(pkg: String?): AnkiTarget? = entries.firstOrNull { it.pkg == pkg }

        /**
         * The app to use: the one the user chose ([saved], a package name) while it is installed; else the only
         * one installed; else myAmgi before AnkiDroid, saying so.
         */
        fun choose(saved: String?, installed: Set<String>): Choice {
            val present = PREFERENCE.filter { it.pkg in installed }
            of(saved)?.takeIf { it in present }?.let { return Choice(it, "", chosenByUser = true) }
            val pick = present.firstOrNull()
                ?: return Choice(null, "AnkiDroid나 myAmgi가 설치되어 있지 않습니다.", chosenByUser = false)
            val note = when {
                present.size == 1 -> "설치된 앱이 ${pick.label} 하나라서 자동으로 연결했습니다."
                pick.isMyAmgi && ANKIDROID in present ->
                    "${pick.label}와 AnkiDroid가 모두 설치되어 있어 ${pick.label}로 연결했습니다. 아래 버튼으로 바꿀 수 있습니다."
                else -> "여러 앱이 설치되어 있어 ${pick.label}로 연결했습니다. 아래 버튼으로 바꿀 수 있습니다."
            }
            return Choice(pick, note, chosenByUser = false)
        }
    }
}
