package com.ggumtak.hanja

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the patched `hanja-loader.apkg` file to AnkiDroid via `ACTION_VIEW` with a temporary read grant.
 *
 * The display name MUST end in `.apkg` and MUST NOT be `"collection.apkg"`: AnkiDroid's `ImportUtils.isCollectionPackage`
 * treats `"collection.apkg"` as a `.colpkg` that replaces the entire collection, whereas `"hanja-loader.apkg"`
 * routes to additive deck import (`CollectionImportAdd`).
 */
class ApkgProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.ggumtak.hanja.apkg"
        const val FILE_NAME = "hanja-loader.apkg"
        const val MIME = "application/apkg"
        val URI: Uri = Uri.parse("content://$AUTHORITY/$FILE_NAME")

        fun dir(ctx: Context): File = File(ctx.cacheDir, "apkg").apply { mkdirs() }

        fun file(ctx: Context): File = File(dir(ctx), FILE_NAME)
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (uri == URI) MIME else null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri != URI) return null
        val ctx = context ?: return null
        val f = file(ctx)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> FILE_NAME
                OpenableColumns.SIZE -> if (f.exists()) f.length() else 0L
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri != URI) throw FileNotFoundException(uri.toString())
        if (mode != "r") throw SecurityException("ApkgProvider is read-only")
        val ctx = context ?: throw FileNotFoundException("No context")
        val f = file(ctx)
        if (!f.exists()) throw FileNotFoundException(FILE_NAME)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
