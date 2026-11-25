package com.ggumtak.hanja

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the Anki code file (AnkiCode) to AnkiDroid's media insert, which reads it from a content URI. Not
 * exported: AnkiDroid reads one file through a one-time grant (AnkiBridge.addCode).
 */
class CodeProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.ggumtak.hanja.code"

        fun dir(ctx: android.content.Context) = File(ctx.filesDir, "anki")

        fun uri(fileName: String): Uri = Uri.parse("content://$AUTHORITY/$fileName")

        private val NAME = Regex("""^_hanja-tap-\d+\.js$""")
    }

    override fun onCreate(): Boolean = true

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!NAME.matches(name)) throw FileNotFoundException(name)
        return File(dir(context!!), name).takeIf { it.isFile } ?: throw FileNotFoundException(name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    /** AnkiDroid names the media file by the extension Android gives this type: whatever type means "js" here. */
    override fun getType(uri: Uri): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension("js") ?: "text/javascript"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
