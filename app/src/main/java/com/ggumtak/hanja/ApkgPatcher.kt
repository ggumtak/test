package com.ggumtak.hanja

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Patches an exported or downloaded Anki `.apkg` (or `.colpkg`) losslessly so huge card templates (over AnkiDroid's
 * ~1MB ContentProvider IPC limit, such as JLPT MAX 덱's 715k–900k-char templates) receive `anki-loader.html` and
 * `--hjtap-code` CSS via AnkiDroid's native Rust importer with zero manual editing and zero data loss.
 *
 * Supports:
 * - Anki 23.10+ v3 packages (`collection.anki21b`, zstd + SQLite Schema V18 + protobuf)
 * - Hybrid v2/Schema V18 packages (e.g. `JLPT-MAX-Deck-2.1.7.apkg`, 1.17GB ZIP64 with `collection.anki21` Schema V18)
 * - Legacy v2/v1 packages (`collection.anki21` / `collection.anki2`, SQLite Schema V11 + JSON)
 *
 * Safety & performance invariants:
 * - Streams via [ZipInputStream] and stops as soon as `collection.anki21b` / `collection.anki21` is extracted
 *   (reads only the first ~19MB of a 1.17GB `.apkg`, never copying gigabytes of audio media to cache).
 * - Always outputs compact Anki v3 (`meta` + zstd `collection.anki21b` + empty zstd `media`) for Schema V18 DBs so
 *   even a 150MB source database produces a tiny ~1–3MB patch package.
 * - Keeps exactly 1 existing note (with its original `id`, `guid`, `mod`, `flds`, `tags`) and its existing cards
 *   per patched notetype so Anki gathers only the patched notetypes, skips the duplicate note under all
 *   `UpdateNotes` modes (`existing.mtime == incoming.mtime`), and never creates cards or touches scheduling.
 * - Never modifies `notetypes.name`/`templates.name` or runs `VACUUM` so Android's SQLite never invokes Anki's
 *   custom `COLLATE unicase`.
 * - Reads large BLOB/TEXT values in <=512KB chunks so multi-megabyte templates never hit Android's 2MB
 *   `CursorWindow` limit.
 */
object ApkgPatcher {
    private const val NOTETYPE_CSS_FIELD = 3
    private const val TEMPLATE_QFMT_FIELD = 1
    private const val TEMPLATE_AFMT_FIELD = 2

    private const val BLOB_CHUNK = 512 * 1024
    private const val TEXT_CHUNK = 250_000
    private const val MTIME_BUMP_SECS = 86_400L

    data class PatchedType(val id: Long, val name: String, val cardCount: Int)

    data class Result(
        val patched: List<PatchedType>,
        val alreadyCurrent: List<String> = emptyList(),
        val unmatched: List<String> = emptyList(),
    )

    /** Minimal SQLite adapter: [openAndroidDb] on device, JDBC `sqlite-jdbc` in JVM unit tests. */
    interface Db : AutoCloseable {
        fun hasTable(name: String): Boolean
        fun query(sql: String): List<List<Any?>>
        fun exec(sql: String, args: Array<Any?> = emptyArray())
        fun inTransaction(block: () -> Unit)
    }

    private enum class Format(val entryName: String, val zstd: Boolean) {
        V3("collection.anki21b", true),
        V2("collection.anki21", false),
        V1("collection.anki2", false),
    }

    /** Patches a local `.apkg` file (convenience wrapper over [patchStream], with [ZipFile] fallback). */
    fun patch(
        input: File,
        output: File,
        loader: String,
        cssBlock: String,
        targetIds: Set<Long> = emptySet(),
        knownTypeIds: Set<Long> = emptySet(),
        nowSecs: Long = System.currentTimeMillis() / 1000L,
        openDb: (File) -> Db = ::openAndroidDb,
    ): Result = patchInternal(
        extract = { dest ->
            runCatching { extractFromStream(BufferedInputStream(FileInputStream(input), 64 * 1024), dest) }
                .getOrElse { extractFromZipFile(input, dest) }
        },
        output = output,
        loader = loader,
        cssBlock = cssBlock,
        targetIds = targetIds,
        knownTypeIds = knownTypeIds,
        nowSecs = nowSecs,
        openDb = openDb,
    )

    /**
     * Streams an `.apkg` directly from [input] (e.g. `ContentResolver.openInputStream(uri)`) without copying
     * gigabytes of trailing audio media to disk. Stops reading immediately after extracting the collection DB.
     */
    fun patchStream(
        input: InputStream,
        output: File,
        loader: String,
        cssBlock: String,
        targetIds: Set<Long> = emptySet(),
        knownTypeIds: Set<Long> = emptySet(),
        nowSecs: Long = System.currentTimeMillis() / 1000L,
        openDb: (File) -> Db = ::openAndroidDb,
    ): Result = patchInternal(
        extract = { dest -> extractFromStream(BufferedInputStream(input, 64 * 1024), dest) },
        output = output,
        loader = loader,
        cssBlock = cssBlock,
        targetIds = targetIds,
        knownTypeIds = knownTypeIds,
        nowSecs = nowSecs,
        openDb = openDb,
    )

    private fun patchInternal(
        extract: (File) -> Format,
        output: File,
        loader: String,
        cssBlock: String,
        targetIds: Set<Long>,
        knownTypeIds: Set<Long>,
        nowSecs: Long,
        openDb: (File) -> Db,
    ): Result {
        val dir = output.parentFile ?: File(".")
        dir.mkdirs()
        val workDb = File.createTempFile("apkg-work-", ".sqlite", dir)
        val tmpOut = File.createTempFile("apkg-out-", ".apkg", dir)
        try {
            val inputFormat = extract(workDb)
            var isSchema18 = false
            val rawResult = openDb(workDb).use { db ->
                if (db.hasTable("notetypes")) {
                    isSchema18 = true
                    patchSchema18(db, loader, cssBlock, targetIds, nowSecs)
                } else if (db.hasTable("col")) {
                    patchSchema11(db, loader, cssBlock, targetIds, nowSecs)
                } else {
                    throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.")
                }
            }
            val unmatched = if (knownTypeIds.isEmpty()) emptyList()
            else rawResult.patched.filter { it.id !in knownTypeIds }.map { it.name }
            val result = rawResult.copy(unmatched = unmatched)
            if (result.patched.isEmpty()) {
                if (result.alreadyCurrent.isNotEmpty()) return result
                throw IllegalStateException(
                    "이 덱 파일에 적용할 수 있는 노트가 없습니다. AnkiDroid에서 카드가 들어 있는 덱을 내보내 주세요."
                )
            }
            // Schema V18 DBs (including hybrid collection.anki21 packs like JLPT MAX) always write compact zstd v3.
            val outFormat = if (isSchema18) Format.V3 else inputFormat
            writePackage(workDb, tmpOut, outFormat)
            output.delete()
            if (!tmpOut.renameTo(output)) {
                tmpOut.copyTo(output, overwrite = true)
                tmpOut.delete()
            }
            return result
        } finally {
            workDb.delete()
            File(workDb.path + "-wal").delete()
            File(workDb.path + "-shm").delete()
            File(workDb.path + "-journal").delete()
            if (tmpOut.exists()) tmpOut.delete()
        }
    }

    private fun extractFromStream(input: InputStream, dest: File): Format {
        val fallback = File.createTempFile("apkg-v1-", ".sqlite", dest.parentFile)
        var hasV1 = false
        var sawAnyEntry = false
        try {
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry = try {
                        zis.nextEntry
                    } catch (e: ZipException) {
                        throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.", e)
                    } ?: break
                    sawAnyEntry = true
                    when (val name = entry.name) {
                        Format.V3.entryName -> {
                            ZstdInputStream(nonClosingIn(zis)).use { src ->
                                FileOutputStream(dest).use { out -> src.copyTo(out, 64 * 1024) }
                            }
                            return Format.V3
                        }
                        Format.V2.entryName -> {
                            FileOutputStream(dest).use { out -> zis.copyTo(out, 64 * 1024) }
                            return Format.V2
                        }
                        Format.V1.entryName -> {
                            // Keep as fallback: v2/v3 exports often include a small collection.anki2 stub first.
                            FileOutputStream(fallback).use { out -> zis.copyTo(out, 64 * 1024) }
                            hasV1 = true
                        }
                        else -> {
                            // Media files ("0", "1", ...) always come after collection DBs; stop early if we have v1.
                            if (hasV1 && name.isNotEmpty() && name.all { it in '0'..'9' }) break
                        }
                    }
                }
            }
            if (hasV1) {
                dest.delete()
                if (!fallback.renameTo(dest)) {
                    fallback.copyTo(dest, overwrite = true)
                }
                return Format.V1
            }
            if (!sawAnyEntry) throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.")
            throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.")
        } finally {
            if (fallback.exists()) fallback.delete()
        }
    }

    private fun extractFromZipFile(input: File, dest: File): Format {
        val zip = try {
            ZipFile(input)
        } catch (e: ZipException) {
            throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.", e)
        } catch (e: Exception) {
            throw IllegalStateException("덱 파일을 열 수 없습니다: ${e.message ?: e.javaClass.simpleName}", e)
        }
        zip.use { z ->
            val format = Format.entries.firstOrNull { z.getEntry(it.entryName) != null }
                ?: throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.")
            val entry = z.getEntry(format.entryName)!!
            val raw = z.getInputStream(entry)
            val stream = if (format.zstd) ZstdInputStream(raw) else raw
            stream.use { src ->
                FileOutputStream(dest).use { out -> src.copyTo(out, 64 * 1024) }
            }
            return format
        }
    }

    private fun writePackage(dbFile: File, dest: File, format: Format) {
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            if (format == Format.V3) {
                zos.putNextEntry(ZipEntry("meta"))
                zos.write(ProtoWire.encodeVarintField(1, 3))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(Format.V3.entryName))
                ZstdOutputStream(nonClosingOut(zos)).use { zstd ->
                    FileInputStream(dbFile).use { src -> src.copyTo(zstd, 64 * 1024) }
                }
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("media"))
                zos.write(Zstd.compress(ByteArray(0)))
                zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry(format.entryName))
                FileInputStream(dbFile).use { src -> src.copyTo(zos, 64 * 1024) }
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("media"))
                zos.write("{}".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun nonClosingIn(src: InputStream): InputStream = object : InputStream() {
        override fun read(): Int = src.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, len)
        override fun close() = Unit
    }

    private fun nonClosingOut(out: OutputStream): OutputStream = object : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = out.flush()
    }

    private fun pickNotesByNotetype(db: Db): Map<Long, Long> {
        val map = LinkedHashMap<Long, Long>()
        // Prefer notes that own at least one card so both note-based and card-based gathering include them.
        if (db.hasTable("cards")) {
            for (row in db.query("SELECT n.mid, MIN(n.id) FROM notes n INNER JOIN cards c ON c.nid = n.id GROUP BY n.mid")) {
                val mid = (row[0] as? Number)?.toLong() ?: continue
                val nid = (row[1] as? Number)?.toLong() ?: continue
                map[mid] = nid
            }
        }
        for (row in db.query("SELECT mid, MIN(id) FROM notes GROUP BY mid")) {
            val mid = (row[0] as? Number)?.toLong() ?: continue
            val nid = (row[1] as? Number)?.toLong() ?: continue
            map.putIfAbsent(mid, nid)
        }
        return map
    }

    private fun pruneToNotes(db: Db, keptNoteIds: Collection<Long>) {
        val inList = keptNoteIds.distinct().joinToString(",")
        db.exec("DELETE FROM notes WHERE id NOT IN ($inList)")
        if (db.hasTable("cards")) db.exec("DELETE FROM cards WHERE nid NOT IN ($inList)")
        if (db.hasTable("revlog")) db.exec("DELETE FROM revlog")
        if (db.hasTable("graves")) db.exec("DELETE FROM graves")
    }

    private class Schema18Type(
        val id: Long,
        val name: String,
        val mtimeSecs: Long,
        val config: ByteArray,
        val css: String,
        val templates: List<Schema18Template>,
    ) {
        val hasLargeSide: Boolean
            get() = templates.any { it.qfmt.length > AnkiTemplate.WRITE_LIMIT || it.afmt.length > AnkiTemplate.WRITE_LIMIT }
        val hasMark: Boolean
            get() = templates.any { AnkiTemplate.has(it.qfmt) || AnkiTemplate.has(it.afmt) }
    }

    private class Schema18Template(
        val ord: Int,
        val config: ByteArray,
        val qfmt: String,
        val afmt: String,
    )

    private fun patchSchema18(
        db: Db,
        loader: String,
        cssBlock: String,
        targetIds: Set<Long>,
        nowSecs: Long,
    ): Result {
        val noteByType = pickNotesByNotetype(db)
        if (noteByType.isEmpty()) return Result(emptyList())

        val ntRows = db.query("SELECT id, name, mtime_secs FROM notetypes")
        val allTypes = mutableListOf<Schema18Type>()
        for (row in ntRows) {
            val id = (row[0] as? Number)?.toLong() ?: continue
            if (id !in noteByType) continue
            val name = row[1] as? String ?: ""
            val mtime = (row[2] as? Number)?.toLong() ?: 0L
            val ntConfig = readBlob(db, "notetypes", "config", "id = $id")
            val css = ProtoWire.getString(ntConfig, NOTETYPE_CSS_FIELD)

            val tmplRows = db.query("SELECT ord FROM templates WHERE ntid = $id ORDER BY ord")
            val templates = tmplRows.mapNotNull { tRow ->
                val ord = (tRow[0] as? Number)?.toInt() ?: return@mapNotNull null
                val tConfig = readBlob(db, "templates", "config", "ntid = $id AND ord = $ord")
                Schema18Template(
                    ord = ord,
                    config = tConfig,
                    qfmt = ProtoWire.getString(tConfig, TEMPLATE_QFMT_FIELD),
                    afmt = ProtoWire.getString(tConfig, TEMPLATE_AFMT_FIELD),
                )
            }
            if (templates.isEmpty()) continue
            allTypes += Schema18Type(id, name, mtime, ntConfig, css, templates)
        }

        val candidates = selectCandidates(allTypes, targetIds, { it.id }, { it.hasLargeSide }, { it.hasMark })
        val patched = mutableListOf<PatchedType>()
        val alreadyCurrent = mutableListOf<String>()
        val keptNotes = mutableListOf<Long>()

        db.inTransaction {
            for (nt in candidates) {
                var changed = false
                val updatedTemplates = mutableListOf<Pair<Int, ByteArray>>()
                for (tmpl in nt.templates) {
                    val newQ = AnkiTemplate.installSafe(tmpl.qfmt, loader)
                    val newA = AnkiTemplate.installSafe(tmpl.afmt, loader)
                    if (newQ != tmpl.qfmt || newA != tmpl.afmt) {
                        changed = true
                        val newCfg = ProtoWire.replaceStrings(
                            tmpl.config,
                            mapOf(TEMPLATE_QFMT_FIELD to newQ, TEMPLATE_AFMT_FIELD to newA),
                        )
                        updatedTemplates += tmpl.ord to newCfg
                    }
                }
                val newCss = if (AnkiCss.isCurrent(nt.css, cssBlock)) nt.css else AnkiCss.install(nt.css, cssBlock)
                val cssChanged = newCss != nt.css
                if (cssChanged) changed = true

                if (!changed) {
                    alreadyCurrent += nt.name
                    continue
                }
                val newMtime = maxOf(nt.mtimeSecs, nowSecs) + MTIME_BUMP_SECS
                val newNtConfig = if (cssChanged) {
                    ProtoWire.replaceStrings(nt.config, mapOf(NOTETYPE_CSS_FIELD to newCss))
                } else {
                    nt.config
                }
                db.exec(
                    "UPDATE notetypes SET mtime_secs = ?, config = ? WHERE id = ?",
                    arrayOf(newMtime, newNtConfig, nt.id),
                )
                for ((ord, newCfg) in updatedTemplates) {
                    db.exec(
                        "UPDATE templates SET mtime_secs = ?, config = ? WHERE ntid = ? AND ord = ?",
                        arrayOf(newMtime, newCfg, nt.id, ord),
                    )
                }
                patched += PatchedType(nt.id, nt.name, nt.templates.size)
                noteByType[nt.id]?.let { keptNotes += it }
            }
            if (keptNotes.isNotEmpty()) pruneToNotes(db, keptNotes)
        }
        return Result(patched, alreadyCurrent)
    }

    private class Schema11Type(
        val key: String,
        val id: Long,
        val name: String,
        val obj: JSONObject,
        val hasLargeSide: Boolean,
        val hasMark: Boolean,
    )

    private fun patchSchema11(
        db: Db,
        loader: String,
        cssBlock: String,
        targetIds: Set<Long>,
        nowSecs: Long,
    ): Result {
        val noteByType = pickNotesByNotetype(db)
        if (noteByType.isEmpty()) return Result(emptyList())

        val modelsJson = readText(db, "col", "models", "1=1")
        if (modelsJson.isBlank()) throw IllegalStateException("올바른 Anki 덱 파일(.apkg)이 아닙니다.")
        val models = JSONObject(modelsJson)

        val allTypes = mutableListOf<Schema11Type>()
        val keys = models.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = models.optJSONObject(key) ?: continue
            val id = obj.optLong("id", key.toLongOrNull() ?: continue)
            if (id !in noteByType) continue
            val tmpls = obj.optJSONArray("tmpls") ?: continue
            if (tmpls.length() == 0) continue
            var large = false
            var mark = false
            for (i in 0 until tmpls.length()) {
                val t = tmpls.optJSONObject(i) ?: continue
                val q = t.optString("qfmt", "")
                val a = t.optString("afmt", "")
                if (q.length > AnkiTemplate.WRITE_LIMIT || a.length > AnkiTemplate.WRITE_LIMIT) large = true
                if (AnkiTemplate.has(q) || AnkiTemplate.has(a)) mark = true
            }
            allTypes += Schema11Type(key, id, obj.optString("name", ""), obj, large, mark)
        }

        val candidates = selectCandidates(allTypes, targetIds, { it.id }, { it.hasLargeSide }, { it.hasMark })
        val patched = mutableListOf<PatchedType>()
        val alreadyCurrent = mutableListOf<String>()
        val keptNotes = mutableListOf<Long>()

        for (nt in candidates) {
            var changed = false
            val tmpls = nt.obj.getJSONArray("tmpls")
            for (i in 0 until tmpls.length()) {
                val t = tmpls.getJSONObject(i)
                val q = t.optString("qfmt", "")
                val a = t.optString("afmt", "")
                val newQ = AnkiTemplate.installSafe(q, loader)
                val newA = AnkiTemplate.installSafe(a, loader)
                if (newQ != q) {
                    t.put("qfmt", newQ)
                    changed = true
                }
                if (newA != a) {
                    t.put("afmt", newA)
                    changed = true
                }
            }
            val css = nt.obj.optString("css", "")
            val newCss = if (AnkiCss.isCurrent(css, cssBlock)) css else AnkiCss.install(css, cssBlock)
            if (newCss != css) {
                nt.obj.put("css", newCss)
                changed = true
            }
            if (!changed) {
                alreadyCurrent += nt.name
                continue
            }
            val oldMod = nt.obj.optLong("mod", 0L)
            nt.obj.put("mod", maxOf(oldMod, nowSecs) + MTIME_BUMP_SECS)
            patched += PatchedType(nt.id, nt.name, tmpls.length())
            noteByType[nt.id]?.let { keptNotes += it }
        }

        if (patched.isNotEmpty()) {
            db.inTransaction {
                db.exec("UPDATE col SET models = ?", arrayOf(models.toString()))
                pruneToNotes(db, keptNotes)
            }
        }
        return Result(patched, alreadyCurrent)
    }

    private fun <T> selectCandidates(
        all: List<T>,
        targetIds: Set<Long>,
        idOf: (T) -> Long,
        isLarge: (T) -> Boolean,
        hasMark: (T) -> Boolean,
    ): List<T> {
        val targeted = all.filter { idOf(it) in targetIds || isLarge(it) || hasMark(it) }
        return targeted.ifEmpty { all }
    }

    internal fun readBlob(db: Db, table: String, column: String, where: String, chunkSize: Int = BLOB_CHUNK): ByteArray {
        val len = (db.query("SELECT length($column) FROM $table WHERE $where").firstOrNull()?.get(0) as? Number)?.toInt() ?: 0
        if (len <= 0) return ByteArray(0)
        if (len <= chunkSize) {
            return db.query("SELECT $column FROM $table WHERE $where").firstOrNull()?.get(0) as? ByteArray ?: ByteArray(0)
        }
        val out = ByteArrayOutputStream(len)
        var pos = 1
        while (pos <= len) {
            val chunk = db.query("SELECT substr($column, $pos, $chunkSize) FROM $table WHERE $where")
                .firstOrNull()?.get(0) as? ByteArray ?: break
            if (chunk.isEmpty()) break
            out.write(chunk)
            pos += chunk.size
        }
        return out.toByteArray()
    }

    internal fun readText(db: Db, table: String, column: String, where: String, chunkSize: Int = TEXT_CHUNK): String {
        val len = (db.query("SELECT length($column) FROM $table WHERE $where").firstOrNull()?.get(0) as? Number)?.toInt() ?: 0
        if (len <= 0) return ""
        if (len <= chunkSize) {
            return db.query("SELECT $column FROM $table WHERE $where").firstOrNull()?.get(0) as? String ?: ""
        }
        val out = StringBuilder(len)
        var pos = 1
        while (pos <= len) {
            val chunk = db.query("SELECT substr($column, $pos, $chunkSize) FROM $table WHERE $where")
                .firstOrNull()?.get(0) as? String ?: break
            if (chunk.isEmpty()) break
            out.append(chunk)
            pos += chunk.codePointCount(0, chunk.length)
        }
        return out.toString()
    }

    private fun openAndroidDb(file: File): Db {
        val sql = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        sql.disableWriteAheadLogging()
        sql.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        return object : Db {
            override fun hasTable(name: String): Boolean =
                sql.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
                    .use { it.moveToFirst() }

            override fun query(sqlText: String): List<List<Any?>> = sql.rawQuery(sqlText, null).use { c ->
                val out = mutableListOf<List<Any?>>()
                val cols = c.columnCount
                while (c.moveToNext()) {
                    val row = ArrayList<Any?>(cols)
                    for (i in 0 until cols) {
                        row += when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                            else -> null
                        }
                    }
                    out += row
                }
                out
            }

            override fun exec(sqlText: String, args: Array<Any?>) {
                if (args.isEmpty()) {
                    sql.execSQL(sqlText)
                    return
                }
                sql.compileStatement(sqlText).use { stmt ->
                    for (i in args.indices) {
                        val idx = i + 1
                        when (val v = args[i]) {
                            null -> stmt.bindNull(idx)
                            is ByteArray -> stmt.bindBlob(idx, v)
                            is Long -> stmt.bindLong(idx, v)
                            is Int -> stmt.bindLong(idx, v.toLong())
                            is Double -> stmt.bindDouble(idx, v)
                            is Float -> stmt.bindDouble(idx, v.toDouble())
                            else -> stmt.bindString(idx, v.toString())
                        }
                    }
                    stmt.execute()
                }
            }

            override fun inTransaction(block: () -> Unit) {
                sql.beginTransaction()
                try {
                    block()
                    sql.setTransactionSuccessful()
                } finally {
                    sql.endTransaction()
                }
            }

            override fun close() {
                sql.close()
            }
        }
    }
}
