package com.ggumtak.hanja

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

/**
 * AnkiDroid's public content provider (FlashCardsContract): note types and their card templates, read and
 * written with the user's "AnkiDroid 데이터베이스" permission. Changing template text needs no full sync.
 * Calls are IPC and may wait for AnkiDroid to open its collection: never on the main thread.
 */
object AnkiBridge {
    const val PACKAGE = "com.ichi2.anki"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private val MODELS: Uri = Uri.parse("content://com.ichi2.anki.flashcards/models")

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun granted(ctx: Context): Boolean = ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun templatesUri(typeId: Long): Uri =
        MODELS.buildUpon().appendPath(typeId.toString()).appendPath("templates").build()

    /** Throws when AnkiDroid refuses (no permission, storage not set up) or cannot be reached. */
    fun noteTypes(cr: ContentResolver): List<AnkiTemplate.NoteType> {
        val types = mutableListOf<Pair<Long, String>>()
        val c = cr.query(MODELS, arrayOf("_id", "name"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid did not answer")
        c.use { while (it.moveToNext()) types += it.getLong(0) to (it.getString(1) ?: "") }
        return types.map { (id, name) -> AnkiTemplate.NoteType(id, name, templates(cr, id)) }.sortedBy { it.name }
    }

    private fun templates(cr: ContentResolver, typeId: Long): List<AnkiTemplate.Template> {
        val out = mutableListOf<AnkiTemplate.Template>()
        val c = cr.query(templatesUri(typeId), arrayOf("ord", "card_template_name", "question_format", "answer_format"),
            null, null, null) ?: throw IllegalStateException("AnkiDroid did not answer")
        c.use {
            while (it.moveToNext()) {
                out += AnkiTemplate.Template(it.getInt(0), it.getString(1) ?: "", it.getString(2) ?: "", it.getString(3) ?: "")
            }
        }
        return out
    }

    private val BASE: Uri = Uri.parse("content://com.ichi2.anki.flashcards")
    private val NOTES: Uri = Uri.withAppendedPath(BASE, "notes")
    private val DECKS: Uri = Uri.withAppendedPath(BASE, "decks")
    private val SCHEDULE: Uri = Uri.withAppendedPath(BASE, "schedule")

    /** Note types by id and name, without their templates. */
    fun noteTypeNames(cr: ContentResolver): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val c = cr.query(MODELS, arrayOf("_id", "name"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid did not answer")
        c.use { while (it.moveToNext()) out += it.getLong(0) to (it.getString(1) ?: "") }
        return out.sortedBy { it.second }
    }

    fun fieldNames(cr: ContentResolver, typeId: Long): List<String> {
        val c = cr.query(Uri.withAppendedPath(MODELS, typeId.toString()), arrayOf("field_names"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid did not answer")
        c.use { if (it.moveToFirst()) return VocabNote.splitFields(it.getString(0) ?: "") }
        throw IllegalStateException("note type $typeId not found")
    }

    /** The fields of up to [limit] notes found by an Anki search; empty when none match. */
    fun notes(cr: ContentResolver, query: String, limit: Int): List<Pair<Long, List<String>>> {
        val out = mutableListOf<Pair<Long, List<String>>>()
        val c = cr.query(NOTES, arrayOf("_id", "flds"), query, null, null) ?: return out
        c.use {
            while (out.size < limit && it.moveToNext()) out += it.getLong(0) to VocabNote.splitFields(it.getString(1) ?: "")
        }
        return out
    }

    /** How many notes an Anki search finds; 0 when none or when AnkiDroid does not answer. */
    fun count(cr: ContentResolver, query: String): Int =
        runCatching { cr.query(NOTES, arrayOf("_id"), query, null, null)?.use { it.count } }.getOrNull() ?: 0

    /** Every deck's full name ("A::B::C") and id. */
    fun decks(cr: ContentResolver): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val c = cr.query(DECKS, arrayOf("deck_id", "deck_name"), null, null, null)
            ?: throw IllegalStateException("AnkiDroid did not answer")
        c.use { while (it.moveToNext()) out += it.getLong(0) to (it.getString(1) ?: "") }
        return out
    }

    /** The id of the deck named [name] ("A::B::C"), created (with its parents) when missing. */
    fun deckId(cr: ContentResolver, name: String): Long {
        decks(cr).firstOrNull { it.second == name }?.let { return it.first }
        val v = ContentValues().apply { put("deck_name", name) }
        val uri = cr.insert(DECKS, v) ?: throw IllegalStateException("deck not created")
        return uri.lastPathSegment!!.toLong()
    }

    /** Adds the note, moves its cards to [deckId] and suspends those [suspend] picks; returns the note id. */
    fun addNote(cr: ContentResolver, typeId: Long, fields: List<String>, tags: String, deckId: Long, suspend: (String) -> Boolean): Long {
        val v = ContentValues().apply {
            put("mid", typeId)
            put("flds", VocabNote.joinFields(fields))
            put("tags", tags)
        }
        val uri = cr.insert(NOTES, v) ?: throw IllegalStateException("note not added")
        val noteId = uri.lastPathSegment!!.toLong()
        val cards = mutableListOf<Pair<Int, String>>()
        val cardsUri = NOTES.buildUpon().appendPath(noteId.toString()).appendPath("cards").build()
        cr.query(cardsUri, arrayOf("ord", "card_name"), null, null, null)?.use {
            while (it.moveToNext()) cards += it.getInt(0) to (it.getString(1) ?: "")
        }
        for ((ord, name) in cards) {
            val one = cardsUri.buildUpon().appendPath(ord.toString()).build()
            cr.update(one, ContentValues().apply { put("deck_id", deckId) }, null, null)
            if (suspend(name)) {
                cr.update(SCHEDULE, ContentValues().apply {
                    put("note_id", noteId)
                    put("ord", ord)
                    put("suspended", 1)
                }, null, null)
            }
        }
        return noteId
    }

    fun write(cr: ContentResolver, change: AnkiTemplate.Change) {
        val v = ContentValues()
        change.front?.let { v.put("question_format", it) }
        change.back?.let { v.put("answer_format", it) }
        if (v.size() == 0) return
        val uri = templatesUri(change.typeId).buildUpon().appendPath(change.ord.toString()).build()
        cr.update(uri, v, null, null)
    }
}
