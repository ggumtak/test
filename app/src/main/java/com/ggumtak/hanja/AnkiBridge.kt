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

    fun write(cr: ContentResolver, change: AnkiTemplate.Change) {
        val v = ContentValues()
        change.front?.let { v.put("question_format", it) }
        change.back?.let { v.put("answer_format", it) }
        if (v.size() == 0) return
        val uri = templatesUri(change.typeId).buildUpon().appendPath(change.ord.toString()).build()
        cr.update(uri, v, null, null)
    }
}
