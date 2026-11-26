package com.ggumtak.hanja

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

/**
 * The Anki app's public content provider (AnkiDroid's FlashCardsContract): note types and their card templates,
 * notes, decks, read and written with the user's permission for that app. The app is the one chosen in settings
 * ([AnkiTarget]: AnkiDroid, myAmgi or myAmgi 코멧); they answer the same API at their own address, so every call
 * here goes through an [Anki] connection that carries the address. Changing template text needs no full sync.
 * Calls are IPC and may wait for the app to open its collection: never on the main thread.
 */
object AnkiBridge {
    /** AnkiDroid's package, for what only AnkiDroid does (the .apkg import screen). */
    const val ANKIDROID_PACKAGE = "com.ichi2.anki"

    /** A connection to one Anki app: its content resolver and address. */
    class Anki(val cr: ContentResolver, val target: AnkiTarget) {
        val base: Uri = Uri.parse("content://" + target.authority)
        val models: Uri = Uri.withAppendedPath(base, "models")
        val notes: Uri = Uri.withAppendedPath(base, "notes")
        val decks: Uri = Uri.withAppendedPath(base, "decks")
        val schedule: Uri = Uri.withAppendedPath(base, "schedule")
    }

    fun installed(ctx: Context, target: AnkiTarget): Boolean = try {
        ctx.packageManager.getPackageInfo(target.pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Which of the Anki apps are installed, by package name. */
    fun installedPackages(ctx: Context): Set<String> =
        AnkiTarget.entries.filter { installed(ctx, it) }.map { it.pkg }.toSet()

    /** The app chosen in settings, or picked by [AnkiTarget.choose] when the user has not chosen. */
    fun choice(ctx: Context): AnkiTarget.Choice = AnkiTarget.choose(Prefs.ankiTarget(ctx), installedPackages(ctx))

    /** The app every call goes to; null when none is installed. */
    fun target(ctx: Context): AnkiTarget? = choice(ctx).target

    /** The chosen app's name for messages ("AnkiDroid" when none is installed). */
    fun label(ctx: Context): String = (target(ctx) ?: AnkiTarget.ANKIDROID).label

    fun installed(ctx: Context): Boolean = target(ctx) != null

    fun granted(ctx: Context): Boolean = target(ctx)?.let { granted(ctx, it) } ?: false

    fun granted(ctx: Context, target: AnkiTarget): Boolean =
        ctx.checkSelfPermission(target.permission) == PackageManager.PERMISSION_GRANTED

    /** The permission to ask for, for the chosen app. */
    fun permission(ctx: Context): String = (target(ctx) ?: AnkiTarget.ANKIDROID).permission

    /** A connection to the chosen app; throws (with a Korean message) when none is installed. */
    fun open(ctx: Context): Anki =
        Anki(ctx.contentResolver, target(ctx) ?: throw IllegalStateException("AnkiDroid나 myAmgi가 설치되어 있지 않습니다."))

    fun open(ctx: Context, target: AnkiTarget): Anki = Anki(ctx.contentResolver, target)

    private fun typeUri(a: Anki, typeId: Long): Uri = a.models.buildUpon().appendPath(typeId.toString()).build()

    /** A note type's CSS (its "styling"), where the Anki code lives (AnkiCss); null when it could not be read. */
    fun css(a: Anki, typeId: Long): String? = runCatching {
        a.cr.query(typeUri(a, typeId), arrayOf("css"), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) ?: "" else null }
    }.getOrNull()

    /** Writes a note type's CSS; null when AnkiDroid took it, else what it answered. */
    fun writeCss(a: Anki, typeId: Long, css: String): String? {
        val answer = runCatching { a.cr.update(typeUri(a, typeId), ContentValues().apply { put("css", css) }, null, null) }
        val rows = answer.getOrNull()
        if (rows != null && rows > 0) return null
        return "CSS ${"%,d".format(css.length)}자, " +
            (answer.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + (it.message ?: "").take(80) } ?: "응답 $rows")
    }

    private fun templatesUri(a: Anki, typeId: Long): Uri =
        a.models.buildUpon().appendPath(typeId.toString()).appendPath("templates").build()

    /** Throws when AnkiDroid refuses (no permission, storage not set up) or cannot be reached. */
    fun noteTypes(a: Anki): List<AnkiTemplate.NoteType> {
        val types = mutableListOf<Pair<Long, String>>()
        val c = a.cr.query(a.models, arrayOf("_id", "name"), null, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use { while (it.moveToNext()) types += it.getLong(0) to (it.getString(1) ?: "") }
        return types.map { (id, name) -> AnkiTemplate.NoteType(id, name, templates(a, id)) }.sortedBy { it.name }
    }

    private fun templates(a: Anki, typeId: Long): List<AnkiTemplate.Template> {
        val out = mutableListOf<AnkiTemplate.Template>()
        val c = a.cr.query(templatesUri(a, typeId), arrayOf("ord", "card_template_name", "question_format", "answer_format"),
            null, null, null) ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use {
            while (it.moveToNext()) {
                out += AnkiTemplate.Template(it.getInt(0), it.getString(1) ?: "", it.getString(2) ?: "", it.getString(3) ?: "")
            }
        }
        return out
    }

    /** Note types by id and name, without their templates. */
    fun noteTypeNames(a: Anki): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val c = a.cr.query(a.models, arrayOf("_id", "name"), null, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use { while (it.moveToNext()) out += it.getLong(0) to (it.getString(1) ?: "") }
        return out.sortedBy { it.second }
    }

    fun fieldNames(a: Anki, typeId: Long): List<String> {
        val c = a.cr.query(typeUri(a, typeId), arrayOf("field_names"), null, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use { if (it.moveToFirst()) return VocabNote.splitFields(it.getString(0) ?: "") }
        throw IllegalStateException("note type $typeId not found")
    }

    /** The fields of up to [limit] notes found by an Anki search; empty when none match. Throws when unreachable. */
    fun notes(a: Anki, query: String, limit: Int): List<Pair<Long, List<String>>> {
        val out = mutableListOf<Pair<Long, List<String>>>()
        val c = a.cr.query(a.notes, arrayOf("_id", "flds"), query, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use {
            while (out.size < limit && it.moveToNext()) out += it.getLong(0) to VocabNote.splitFields(it.getString(1) ?: "")
        }
        return out
    }

    /** Up to [limit] note ids found by an Anki search (queries `_id` only, avoiding CursorWindow overflow). */
    fun noteIds(a: Anki, query: String, limit: Int): List<Long> {
        val out = mutableListOf<Long>()
        val c = a.cr.query(a.notes, arrayOf("_id"), query, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use {
            while (out.size < limit && it.moveToNext()) out += it.getLong(0)
        }
        return out
    }

    /** The fields of a single note by [noteId], or null when not found. */
    fun noteFields(a: Anki, noteId: Long): List<String>? {
        val c = a.cr.query(Uri.withAppendedPath(a.notes, noteId.toString()), arrayOf("flds"), null, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use {
            if (it.moveToFirst()) return VocabNote.splitFields(it.getString(0) ?: "")
        }
        return null
    }

    /** How many notes an Anki search finds; 0 when none or when AnkiDroid does not answer. */
    fun count(a: Anki, query: String): Int =
        runCatching { a.cr.query(a.notes, arrayOf("_id"), query, null, null)?.use { it.count } }.getOrNull() ?: 0

    /** Every deck's full name ("A::B::C") and id. */
    fun decks(a: Anki): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val c = a.cr.query(a.decks, arrayOf("deck_id", "deck_name"), null, null, null)
            ?: throw IllegalStateException("${a.target.label} did not answer")
        c.use { while (it.moveToNext()) out += it.getLong(0) to (it.getString(1) ?: "") }
        return out
    }

    /** The id of the deck named [name] ("A::B::C"), created (with its parents) when missing. */
    fun deckId(a: Anki, name: String): Long {
        decks(a).firstOrNull { it.second == name }?.let { return it.first }
        val v = ContentValues().apply { put("deck_name", name) }
        val uri = a.cr.insert(a.decks, v) ?: throw IllegalStateException("deck not created")
        return uri.lastPathSegment!!.toLong()
    }

    /** Adds the note, moves its cards to [deckId] and suspends those [suspend] picks; returns the note id. */
    fun addNote(a: Anki, typeId: Long, fields: List<String>, tags: String, deckId: Long, suspend: (String) -> Boolean): Long {
        val v = ContentValues().apply {
            put("mid", typeId)
            put("flds", VocabNote.joinFields(fields))
            put("tags", tags)
        }
        val uri = a.cr.insert(a.notes, v) ?: throw IllegalStateException("note not added")
        val noteId = uri.lastPathSegment!!.toLong()
        try {
            val cards = mutableListOf<Pair<Int, String>>()
            val cardsUri = a.notes.buildUpon().appendPath(noteId.toString()).appendPath("cards").build()
            val c = a.cr.query(cardsUri, arrayOf("ord", "card_name"), null, null, null)
                ?: throw IllegalStateException("${a.target.label} did not answer")
            c.use {
                while (it.moveToNext()) cards += it.getInt(0) to (it.getString(1) ?: "")
            }
            if (cards.isEmpty()) throw IllegalStateException("no cards created")
            for ((ord, name) in cards) {
                val one = cardsUri.buildUpon().appendPath(ord.toString()).build()
                a.cr.update(one, ContentValues().apply { put("deck_id", deckId) }, null, null)
                if (suspend(name)) {
                    a.cr.update(a.schedule, ContentValues().apply {
                        put("note_id", noteId)
                        put("ord", ord)
                        put("suspended", 1)
                    }, null, null)
                }
            }
            return noteId
        } catch (t: Throwable) {
            runCatching { a.cr.delete(uri, null, null) }
            throw t
        }
    }

    /**
     * Writes one side per call: a deck's templates can be large, and front and back together may not fit in one
     * call to AnkiDroid (about 500,000 characters), which then fails without an error. Returns a note for each
     * side AnkiDroid did not take; empty when both went in.
     */
    fun write(a: Anki, change: AnkiTemplate.Change): List<String> {
        val uri = templatesUri(a, change.typeId).buildUpon().appendPath(change.ord.toString()).build()
        val notes = mutableListOf<String>()
        fun side(label: String, column: String, text: String?) {
            if (text == null) return
            val answer = runCatching { a.cr.update(uri, ContentValues().apply { put(column, text) }, null, null) }
            val rows = answer.getOrNull()
            if (rows == null || rows <= 0) {
                val why = answer.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + (it.message ?: "").take(80) } ?: "응답 $rows"
                notes += "$label ${"%,d".format(text.length)}자, $why"
            }
        }
        side("앞면", "question_format", change.front)
        side("뒷면", "answer_format", change.back)
        return notes
    }
}
