package com.ggumtak.hanja

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sqlite.Collation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkgPatcherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val loader = File("src/main/assets/anki-loader.html").readText()
    private val cssBlock = AnkiCss.block(File("src/main/assets/anki-tap.js").readBytes())
    private val oldV6 = "<!-- 한자 바로보기 v6 (AnkiDroid) -->\n<style>a.hjtap{}</style>\n<script>window.__hjtap6=function(){};</script>"

    /** JDBC adapter for [ApkgPatcher.Db] WITHOUT registering `unicase` (matching Android SQLite). */
    private fun openJdbc(file: File): ApkgPatcher.Db {
        val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        return object : ApkgPatcher.Db {
            override fun hasTable(name: String): Boolean =
                conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { it.next() }
                }

            override fun query(sql: String): List<List<Any?>> =
                conn.createStatement().use { st ->
                    st.executeQuery(sql).use { rs ->
                        val cols = rs.metaData.columnCount
                        val out = mutableListOf<List<Any?>>()
                        while (rs.next()) {
                            val row = ArrayList<Any?>(cols)
                            for (i in 1..cols) row += rs.getObject(i)
                            out += row
                        }
                        out
                    }
                }

            override fun exec(sql: String, args: Array<Any?>) {
                if (args.isEmpty()) {
                    conn.createStatement().use { it.execute(sql) }
                    return
                }
                conn.prepareStatement(sql).use { ps ->
                    for (i in args.indices) {
                        val idx = i + 1
                        when (val v = args[i]) {
                            null -> ps.setObject(idx, null)
                            is ByteArray -> ps.setBytes(idx, v)
                            is Long -> ps.setLong(idx, v)
                            is Int -> ps.setInt(idx, v)
                            is Double -> ps.setDouble(idx, v)
                            is Float -> ps.setFloat(idx, v)
                            else -> ps.setString(idx, v.toString())
                        }
                    }
                    ps.executeUpdate()
                }
            }

            override fun inTransaction(block: () -> Unit) {
                conn.autoCommit = false
                try {
                    block()
                    conn.commit()
                } catch (t: Throwable) {
                    conn.rollback()
                    throw t
                } finally {
                    conn.autoCommit = true
                }
            }

            override fun close() {
                conn.close()
            }
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
    }

    private fun fieldVarint(num: Int, v: Long): ByteArray = ProtoWire.encodeVarintField(num, v)

    private fun fieldBytes(num: Int, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarint(out, (num.toLong() shl 3) or 2L)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
        return out.toByteArray()
    }

    private fun fieldString(num: Int, s: String): ByteArray = fieldBytes(num, s.toByteArray(Charsets.UTF_8))

    private fun fieldFixed64(num: Int, v: Long): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarint(out, (num.toLong() shl 3) or 1L)
        for (i in 0 until 8) out.write(((v ushr (i * 8)) and 0xFFL).toInt())
        return out.toByteArray()
    }

    private fun fieldFixed32(num: Int, v: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarint(out, (num.toLong() shl 3) or 5L)
        for (i in 0 until 4) out.write((v ushr (i * 8)) and 0xFF)
        return out.toByteArray()
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    private fun registerUnicase(conn: Connection) {
        Collation.create(conn, "unicase", object : Collation() {
            override fun xCompare(str1: String, str2: String): Int = str1.compareTo(str2, ignoreCase = true)
        })
    }

    @Test
    fun protoWirePreservesAllOtherWireTypesAndHighFieldNumbers() {
        val meta = ProtoWire.encodeVarintField(1, 3)
        assertArrayEquals(byteArrayOf(0x08, 0x03), meta)
        assertEquals(3L, ProtoWire.getVarint(meta, 1))

        val otherBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val original = concat(
            fieldVarint(1, 1),
            fieldVarint(2, 42),
            fieldString(3, ".old { color: red; }"),
            fieldFixed64(4, 0x0102030405060708L),
            fieldFixed32(5, 0x11223344),
            fieldVarint(10, 1700000000123L),
            fieldBytes(255, otherBytes),
        )
        assertEquals(".old { color: red; }", ProtoWire.getString(original, 3))

        val updated = ProtoWire.replaceStrings(original, mapOf(3 to ".new { color: blue; }"))
        assertEquals(".new { color: blue; }", ProtoWire.getString(updated, 3))
        assertEquals(1L, ProtoWire.getVarint(updated, 1))
        assertEquals(42L, ProtoWire.getVarint(updated, 2))
        assertEquals(1700000000123L, ProtoWire.getVarint(updated, 10))
        // Replacing field 3 back to original and removing it must leave identical preserved bytes.
        val strippedOriginal = ProtoWire.replaceStrings(original, mapOf(3 to ""))
        val strippedUpdated = ProtoWire.replaceStrings(updated, mapOf(3 to ""))
        assertArrayEquals(strippedOriginal, strippedUpdated)
    }

    @Test
    fun patchesRealSchema18V3ApkgWithUnicaseCollationWithoutRegisteringUnicase() {
        val dbFile = tmp.newFile("src18.sqlite")
        val jlptId = 1700000000001L
        val basicId = 1700000000002L
        val unusedId = 1700000000003L
        val oldMtime = 1700000100L
        val noteMod = 1700000500L

        val hugeFront = "<div class='jp'>" + "漢字".repeat(260_000) + "</div>" // 520k+ chars (> WRITE_LIMIT, > 1.5MB UTF-8)
        val hugeBack = "<div class='kr'>" + "뜻".repeat(260_000) + "</div>\n$oldV6"
        val ntConfig = concat(
            fieldVarint(2, 2), // sort_field_idx
            fieldString(3, ".card { font-size: 20px; }"),
            fieldVarint(10, 999L), // original_id
            fieldBytes(255, byteArrayOf(1, 2, 3)),
        )
        val tmpl0Config = concat(
            fieldString(1, hugeFront),
            fieldString(2, hugeBack),
            fieldVarint(5, 555L), // target_deck_id
            fieldString(6, "Noto Sans JP"), // browser_font_name
            fieldVarint(8, 777L), // template id
        )
        val tmpl1Config = concat(
            fieldString(1, "<div class='audio'>" + "音".repeat(250_000) + "</div>"),
            fieldString(2, "{{FrontSide}}<hr id=answer>{{뜻}}"),
            fieldVarint(8, 778L),
        )
        val basicNtConfig = fieldString(3, ".card { color: gray; }")
        val basicTmplConfig = concat(fieldString(1, "{{Front}}"), fieldString(2, "{{Back}}"))

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            registerUnicase(conn)
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE notetypes (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE unicase, mtime_secs INTEGER NOT NULL, usn INTEGER NOT NULL, config BLOB NOT NULL)")
                st.execute("CREATE UNIQUE INDEX ix_notetypes_name ON notetypes (name)")
                st.execute("CREATE TABLE templates (ntid INTEGER NOT NULL, ord INTEGER NOT NULL, name TEXT NOT NULL COLLATE unicase, mtime_secs INTEGER NOT NULL, usn INTEGER NOT NULL, config BLOB NOT NULL, PRIMARY KEY (ntid, ord))")
                st.execute("CREATE UNIQUE INDEX ix_templates_name ON templates (ntid, name)")
                st.execute("CREATE TABLE fields (ntid INTEGER NOT NULL, ord INTEGER NOT NULL, name TEXT NOT NULL COLLATE unicase, config BLOB NOT NULL, PRIMARY KEY (ntid, ord))")
                st.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, tags TEXT NOT NULL, flds TEXT NOT NULL, sfld INTEGER NOT NULL, csum INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
                st.execute("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, ord INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, type INTEGER NOT NULL, queue INTEGER NOT NULL, due INTEGER NOT NULL, ivl INTEGER NOT NULL, factor INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, left INTEGER NOT NULL, odue INTEGER NOT NULL, odid INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
                st.execute("CREATE TABLE revlog (id INTEGER PRIMARY KEY, cid INTEGER NOT NULL, usn INTEGER NOT NULL, ease INTEGER NOT NULL, ivl INTEGER NOT NULL, lastIvl INTEGER NOT NULL, factor INTEGER NOT NULL, time INTEGER NOT NULL, type INTEGER NOT NULL)")
                st.execute("CREATE TABLE graves (usn INTEGER NOT NULL, oid INTEGER NOT NULL, type INTEGER NOT NULL)")
            }
            conn.prepareStatement("INSERT INTO notetypes VALUES (?,?,?,?,?)").use { ps ->
                fun addNt(id: Long, name: String, cfg: ByteArray) {
                    ps.setLong(1, id); ps.setString(2, name); ps.setLong(3, oldMtime); ps.setInt(4, -1); ps.setBytes(5, cfg)
                    ps.executeUpdate()
                }
                addNt(jlptId, "JLPT MAX덱 어휘", ntConfig)
                addNt(basicId, "Basic", basicNtConfig)
                addNt(unusedId, "Unused", basicNtConfig)
            }
            conn.prepareStatement("INSERT INTO templates VALUES (?,?,?,?,?,?)").use { ps ->
                fun addT(ntid: Long, ord: Int, name: String, cfg: ByteArray) {
                    ps.setLong(1, ntid); ps.setInt(2, ord); ps.setString(3, name); ps.setLong(4, oldMtime); ps.setInt(5, -1); ps.setBytes(6, cfg)
                    ps.executeUpdate()
                }
                addT(jlptId, 0, "어휘", tmpl0Config)
                addT(jlptId, 1, "음성", tmpl1Config)
                addT(basicId, 0, "Card 1", basicTmplConfig)
                addT(unusedId, 0, "Card 1", basicTmplConfig)
            }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO notes VALUES (101, 'g101', $jlptId, $noteMod, 0, ' n1 ', '漢字\u001f뜻', 0, 1, 0, '')")
                st.execute("INSERT INTO notes VALUES (102, 'g102', $jlptId, $noteMod, 0, ' n2 ', '学校\u001f학교', 0, 2, 0, '')")
                st.execute("INSERT INTO notes VALUES (201, 'g201', $basicId, $noteMod, 0, '', 'Hello\u001f안녕', 0, 3, 0, '')")
                st.execute("INSERT INTO cards VALUES (1001, 101, 10, 0, $noteMod, 0, 2, 2, 100, 30, 2500, 5, 0, 0, 0, 0, 0, '')")
                st.execute("INSERT INTO cards VALUES (1002, 101, 10, 1, $noteMod, 0, 2, 2, 100, 30, 2500, 5, 0, 0, 0, 0, 0, '')")
                st.execute("INSERT INTO cards VALUES (1003, 102, 10, 0, $noteMod, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')")
                st.execute("INSERT INTO cards VALUES (2001, 201, 1, 0, $noteMod, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')")
                st.execute("INSERT INTO revlog VALUES (9001, 1001, 0, 3, 30, 15, 2500, 1200, 1)")
                st.execute("INSERT INTO graves VALUES (0, 888, 0)")
            }
        }

        // Pack into a realistic Anki v3 .apkg (including dummy collection.anki2 stub & unrelated media file).
        val inputApkg = tmp.newFile("jlpt-export.apkg")
        ZipOutputStream(FileOutputStream(inputApkg)).use { zos ->
            zos.putNextEntry(ZipEntry("meta"))
            zos.write(ProtoWire.encodeVarintField(1, 3))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("collection.anki21b"))
            val zstdBytes = Zstd.compress(dbFile.readBytes())
            zos.write(zstdBytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("collection.anki2"))
            zos.write("not-a-real-db-stub".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("media"))
            zos.write(Zstd.compress(ByteArray(0)))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("0"))
            zos.write("dummy-audio-bytes".toByteArray())
            zos.closeEntry()
        }

        val outputApkg = File(tmp.root, "hanja-loader.apkg")
        val nowSecs = 1764000000L
        val result = ApkgPatcher.patch(
            input = inputApkg,
            output = outputApkg,
            loader = loader,
            cssBlock = cssBlock,
            targetIds = emptySet(),
            nowSecs = nowSecs,
            openDb = ::openJdbc,
        )

        assertEquals(listOf(ApkgPatcher.PatchedType(jlptId, "JLPT MAX덱 어휘", 2)), result.patched)
        assertTrue(outputApkg.exists())

        // Verify output ZIP structure & decompress collection.anki21b.
        val outDb = tmp.newFile("out18.sqlite")
        ZipFile(outputApkg).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("meta", "collection.anki21b", "media"), names)
            assertArrayEquals(byteArrayOf(0x08, 0x03), zip.getInputStream(zip.getEntry("meta")).readBytes())
            val mediaDecompressed = ZstdInputStream(zip.getInputStream(zip.getEntry("media"))).use { it.readBytes() }
            assertEquals(0, mediaDecompressed.size)
            ZstdInputStream(zip.getInputStream(zip.getEntry("collection.anki21b"))).use { src ->
                FileOutputStream(outDb).use { dst -> src.copyTo(dst) }
            }
        }

        openJdbc(outDb).use { db ->
            // Notetype CSS + mtime updated, all other protobuf fields preserved.
            val ntRow = db.query("SELECT mtime_secs, config FROM notetypes WHERE id = $jlptId").single()
            assertEquals(nowSecs + 86_400L, (ntRow[0] as Number).toLong())
            val patchedNtCfg = ntRow[1] as ByteArray
            val patchedCss = ProtoWire.getString(patchedNtCfg, 3)
            assertTrue(patchedCss.contains(".card { font-size: 20px; }"))
            assertTrue(AnkiCss.isCurrent(patchedCss, cssBlock))
            assertEquals(2L, ProtoWire.getVarint(patchedNtCfg, 2))
            assertEquals(999L, ProtoWire.getVarint(patchedNtCfg, 10))

            // Both templates patched cleanly, old v6 stripped, unknown protobuf fields preserved.
            val t0Row = db.query("SELECT mtime_secs, config FROM templates WHERE ntid = $jlptId AND ord = 0").single()
            assertEquals(nowSecs + 86_400L, (t0Row[0] as Number).toLong())
            val t0Cfg = t0Row[1] as ByteArray
            val q0 = ProtoWire.getString(t0Cfg, 1)
            val a0 = ProtoWire.getString(t0Cfg, 2)
            assertTrue(AnkiTemplate.isCurrent(q0, loader))
            assertTrue(AnkiTemplate.isCurrent(a0, loader))
            assertFalse(a0.contains("__hjtap6"))
            assertFalse(AnkiTemplate.broken(a0))
            assertEquals(555L, ProtoWire.getVarint(t0Cfg, 5))
            assertEquals("Noto Sans JP", ProtoWire.getString(t0Cfg, 6))
            assertEquals(777L, ProtoWire.getVarint(t0Cfg, 8))

            val t1Cfg = db.query("SELECT config FROM templates WHERE ntid = $jlptId AND ord = 1").single()[0] as ByteArray
            assertTrue(AnkiTemplate.isCurrent(ProtoWire.getString(t1Cfg, 1), loader))
            assertTrue(AnkiTemplate.isCurrent(ProtoWire.getString(t1Cfg, 2), loader))
            assertEquals(778L, ProtoWire.getVarint(t1Cfg, 8))

            // Exactly 1 existing note kept with untouched mod/guid/flds/tags; Basic notes pruned!
            val notes = db.query("SELECT id, guid, mid, mod, tags, flds FROM notes")
            assertEquals(1, notes.size)
            assertEquals(101L, (notes[0][0] as Number).toLong())
            assertEquals("g101", notes[0][1])
            assertEquals(jlptId, (notes[0][2] as Number).toLong())
            assertEquals(noteMod, (notes[0][3] as Number).toLong())
            assertEquals(" n1 ", notes[0][4])
            assertEquals("漢字\u001f뜻", notes[0][5])

            // Only note 101's cards kept; revlog and graves empty.
            val cardIds = db.query("SELECT id FROM cards ORDER BY id").map { (it[0] as Number).toLong() }
            assertEquals(listOf(1001L, 1002L), cardIds)
            assertTrue(db.query("SELECT id FROM revlog").isEmpty())
            assertTrue(db.query("SELECT oid FROM graves").isEmpty())
        }

        // Running patch again on the already-patched package reports alreadyCurrent and does not re-patch.
        val out2 = File(tmp.root, "second.apkg")
        val again = ApkgPatcher.patch(outputApkg, out2, loader, cssBlock, emptySet(), emptySet(), nowSecs + 100, ::openJdbc)
        assertTrue(again.patched.isEmpty())
        assertEquals(listOf("JLPT MAX덱 어휘"), again.alreadyCurrent)
        assertFalse(out2.exists())
    }

    @Test
    fun patchesLegacySchema11V2Apkg() {
        val dbFile = tmp.newFile("src11.sqlite")
        val jlptId = 1600000000001L
        val basicId = 1600000000002L
        val models = JSONObject().apply {
            put(jlptId.toString(), JSONObject().apply {
                put("id", jlptId)
                put("name", "JLPT MAX덱 문법")
                put("mod", 1600000000L)
                put("css", ".card { font-size: 18px; }")
                put("tmpls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "문법")
                        put("ord", 0)
                        put("qfmt", "<div>" + "文".repeat(490_000) + "</div>")
                        put("afmt", "{{FrontSide}}<hr id=answer>{{해설}}")
                    })
                })
            })
            put(basicId.toString(), JSONObject().apply {
                put("id", basicId)
                put("name", "Basic")
                put("mod", 1600000000L)
                put("css", "")
                put("tmpls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "Card 1")
                        put("ord", 0)
                        put("qfmt", "{{Front}}")
                        put("afmt", "{{Back}}")
                    })
                })
            })
        }

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE col (id INTEGER PRIMARY KEY, models TEXT NOT NULL)")
                st.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL, mod INTEGER NOT NULL, tags TEXT NOT NULL, flds TEXT NOT NULL)")
                st.execute("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, ord INTEGER NOT NULL)")
                st.execute("CREATE TABLE revlog (id INTEGER PRIMARY KEY, cid INTEGER NOT NULL)")
                st.execute("CREATE TABLE graves (usn INTEGER NOT NULL, oid INTEGER NOT NULL, type INTEGER NOT NULL)")
            }
            conn.prepareStatement("INSERT INTO col VALUES (1, ?)").use { ps ->
                ps.setString(1, models.toString())
                ps.executeUpdate()
            }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO notes VALUES (501, 'g501', $jlptId, 1600000500, '', '文法')")
                st.execute("INSERT INTO notes VALUES (502, 'g502', $jlptId, 1600000501, '', '例文')")
                st.execute("INSERT INTO notes VALUES (601, 'g601', $basicId, 1600000502, '', 'A')")
                st.execute("INSERT INTO cards VALUES (5001, 501, 1, 0)")
                st.execute("INSERT INTO cards VALUES (5002, 502, 1, 0)")
                st.execute("INSERT INTO cards VALUES (6001, 601, 1, 0)")
                st.execute("INSERT INTO revlog VALUES (1, 5001)")
                st.execute("INSERT INTO graves VALUES (0, 99, 0)")
            }
        }

        val inputApkg = tmp.newFile("legacy.apkg")
        ZipOutputStream(FileOutputStream(inputApkg)).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki21"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("collection.anki2"))
            zos.write("stub".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("media"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val outputApkg = File(tmp.root, "legacy-out.apkg")
        val nowSecs = 1764000000L
        val res = ApkgPatcher.patch(inputApkg, outputApkg, loader, cssBlock, emptySet(), emptySet(), nowSecs, ::openJdbc)
        assertEquals(listOf(ApkgPatcher.PatchedType(jlptId, "JLPT MAX덱 문법", 1)), res.patched)

        val outDb = tmp.newFile("out11.sqlite")
        ZipFile(outputApkg).use { zip ->
            assertEquals(setOf("collection.anki21", "media"), zip.entries().asSequence().map { it.name }.toSet())
            assertEquals("{}", zip.getInputStream(zip.getEntry("media")).readBytes().toString(Charsets.UTF_8))
            zip.getInputStream(zip.getEntry("collection.anki21")).use { src ->
                FileOutputStream(outDb).use { dst -> src.copyTo(dst) }
            }
        }

        openJdbc(outDb).use { db ->
            val outModels = JSONObject(db.query("SELECT models FROM col").single()[0] as String)
            val m = outModels.getJSONObject(jlptId.toString())
            assertEquals(nowSecs + 86_400L, m.getLong("mod"))
            assertTrue(AnkiCss.isCurrent(m.getString("css"), cssBlock))
            val tmpl = m.getJSONArray("tmpls").getJSONObject(0)
            assertTrue(AnkiTemplate.isCurrent(tmpl.getString("qfmt"), loader))
            assertTrue(AnkiTemplate.isCurrent(tmpl.getString("afmt"), loader))

            val noteIds = db.query("SELECT id FROM notes").map { (it[0] as Number).toLong() }
            assertEquals(listOf(501L), noteIds)
            val cardIds = db.query("SELECT id FROM cards").map { (it[0] as Number).toLong() }
            assertEquals(listOf(5001L), cardIds)
            assertTrue(db.query("SELECT id FROM revlog").isEmpty())
            assertTrue(db.query("SELECT oid FROM graves").isEmpty())
        }
    }

    @Test
    fun streamPatchSkipsTrailingMediaOutputsV3ForHybridSchema18AndReportsUnmatchedIds() {
        // Mirrors real JLPT-MAX-Deck-2.1.7.apkg: collection.anki2 stub first, collection.anki21 (Schema V18) second,
        // followed by huge media blobs that must NEVER be read by patchStream.
        val dbFile = tmp.newFile("hybrid18.sqlite")
        val jlptId = 362224986279207015L
        val ntCfg = fieldString(3, ".card{font-size:20px;}")
        val tCfg = concat(fieldString(1, "<div>" + "語".repeat(500_000) + "</div>"), fieldString(2, "<div>{{Back}}</div>"))
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            registerUnicase(conn)
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE notetypes (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE unicase, mtime_secs INTEGER NOT NULL, usn INTEGER NOT NULL, config BLOB NOT NULL)")
                st.execute("CREATE TABLE templates (ntid INTEGER NOT NULL, ord INTEGER NOT NULL, name TEXT NOT NULL COLLATE unicase, mtime_secs INTEGER NOT NULL, usn INTEGER NOT NULL, config BLOB NOT NULL, PRIMARY KEY (ntid, ord))")
                st.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, tags TEXT NOT NULL, flds TEXT NOT NULL, sfld INTEGER NOT NULL, csum INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
                st.execute("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, ord INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, type INTEGER NOT NULL, queue INTEGER NOT NULL, due INTEGER NOT NULL, ivl INTEGER NOT NULL, factor INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, left INTEGER NOT NULL, odue INTEGER NOT NULL, odid INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
            }
            conn.prepareStatement("INSERT INTO notetypes VALUES (?,?,?,?,?)").use { ps ->
                ps.setLong(1, jlptId); ps.setString(2, "JLPT MAX덱 어휘"); ps.setLong(3, 1794678865L); ps.setInt(4, 0); ps.setBytes(5, ntCfg)
                ps.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO templates VALUES (?,?,?,?,?,?)").use { ps ->
                ps.setLong(1, jlptId); ps.setInt(2, 0); ps.setString(3, "어휘"); ps.setLong(4, 1794678865L); ps.setInt(5, 0); ps.setBytes(6, tCfg)
                ps.executeUpdate()
            }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO notes VALUES (1, 'g1', $jlptId, 1794678865, 0, '', '言う', 0, 1, 0, '')")
                st.execute("INSERT INTO cards VALUES (10, 1, 1, 0, 1794678865, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')")
            }
        }

        val hybridApkg = tmp.newFile("jlpt-hybrid.apkg")
        val hugeMedia = ByteArray(2 * 1024 * 1024).also { java.util.Random(42).nextBytes(it) }
        ZipOutputStream(FileOutputStream(hybridApkg)).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki2"))
            zos.write("stub-anki2".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("collection.anki21"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("0"))
            zos.write(hugeMedia)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("media"))
            zos.write("""{"0":"a.mp3"}""".toByteArray())
            zos.closeEntry()
        }

        var bytesRead = 0L
        val counting = object : java.io.FilterInputStream(hybridApkg.inputStream()) {
            override fun read(): Int {
                val r = super.read()
                if (r >= 0) bytesRead++
                return r
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) bytesRead += n
                return n
            }
        }

        val out = File(tmp.root, "hybrid-out.apkg")
        val res = counting.use {
            ApkgPatcher.patchStream(
                input = it,
                output = out,
                loader = loader,
                cssBlock = cssBlock,
                targetIds = emptySet(),
                knownTypeIds = setOf(999_999L), // does not match jlptId -> unmatched reported
                nowSecs = 1795600000L,
                openDb = ::openJdbc,
            )
        }
        assertEquals(listOf("JLPT MAX덱 어휘"), res.patched.map { it.name })
        assertEquals(listOf("JLPT MAX덱 어휘"), res.unmatched)
        // Stopped immediately after collection.anki21 without reading the 2MB media entry!
        assertTrue("Expected early stop before 2MB media, read $bytesRead / ${hybridApkg.length()}", bytesRead < hybridApkg.length() / 2)
        // Always upgraded Schema V18 output to compact v3 (meta + zstd collection.anki21b + zstd media).
        ZipFile(out).use { z ->
            assertEquals(setOf("meta", "collection.anki21b", "media"), z.entries().asSequence().map { it.name }.toSet())
        }
    }

    @Test
    fun chunkedReadersReconstructMultiChunkBlobAndUnicodeTextExact() {
        val dbFile = tmp.newFile("chunks.sqlite")
        val blob = ByteArray(5000) { (it * 31 + 7).toByte() }
        val text = buildString {
            repeat(1500) { append("🔊💡𠮟漢字かな한글A") }
        }
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { it.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, b BLOB, s TEXT)") }
            conn.prepareStatement("INSERT INTO t VALUES (1, ?, ?)").use { ps ->
                ps.setBytes(1, blob)
                ps.setString(2, text)
                ps.executeUpdate()
            }
        }
        openJdbc(dbFile).use { db ->
            assertArrayEquals(blob, ApkgPatcher.readBlob(db, "t", "b", "id = 1", chunkSize = 777))
            assertEquals(text, ApkgPatcher.readText(db, "t", "s", "id = 1", chunkSize = 777))
        }
    }

    @Test
    fun patchesSchema11Over250kCodepointsWithSupplementaryEmojisAndKanjiWithoutCorruption() {
        val dbFile = tmp.newFile("src11-nonbmp.sqlite")
        val typeId = 1650000000001L
        // Exceeds 250,000 codepoints and places supplementary characters (🔊💡𠮟) well before the 250k chunk boundary.
        val nonBmpPrefix = "🔊💡𠮟".repeat(10_000) // 30,000 codepoints (60,000 UTF-16 code units!)
        val bodyPad = "漢".repeat(480_000)
        val originalFront = "<div class='front'>$nonBmpPrefix$bodyPad{{Expression}}</div>"
        val originalBack = "<div class='back'>💡𠮟🔊{{Meaning}}</div>"
        val models = JSONObject().apply {
            put(typeId.toString(), JSONObject().apply {
                put("id", typeId)
                put("name", "NonBMP 어휘")
                put("mod", 1650000000L)
                put("css", ".card { content: '🔊💡𠮟'; }")
                put("tmpls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "어휘")
                        put("ord", 0)
                        put("qfmt", originalFront)
                        put("afmt", originalBack)
                    })
                })
            })
        }

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE col (id INTEGER PRIMARY KEY, models TEXT NOT NULL)")
                st.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL, mod INTEGER NOT NULL, tags TEXT NOT NULL, flds TEXT NOT NULL)")
                st.execute("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, ord INTEGER NOT NULL)")
            }
            conn.prepareStatement("INSERT INTO col VALUES (1, ?)").use { ps ->
                ps.setString(1, models.toString())
                ps.executeUpdate()
            }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO notes VALUES (701, 'g701', $typeId, 1650000100, '', '𠮟る')")
                st.execute("INSERT INTO cards VALUES (7001, 701, 1, 0)")
            }
        }

        val inputApkg = tmp.newFile("nonbmp11.apkg")
        ZipOutputStream(FileOutputStream(inputApkg)).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki21"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("media"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val outputApkg = File(tmp.root, "nonbmp11-out.apkg")
        val res = ApkgPatcher.patch(inputApkg, outputApkg, loader, cssBlock, emptySet(), emptySet(), 1764000000L, ::openJdbc)
        assertEquals(listOf(ApkgPatcher.PatchedType(typeId, "NonBMP 어휘", 1)), res.patched)

        val outDb = tmp.newFile("out11-nonbmp.sqlite")
        ZipFile(outputApkg).use { zip ->
            zip.getInputStream(zip.getEntry("collection.anki21")).use { src ->
                FileOutputStream(outDb).use { dst -> src.copyTo(dst) }
            }
        }
        openJdbc(outDb).use { db ->
            val outModels = JSONObject(ApkgPatcher.readText(db, "col", "models", "1=1"))
            val m = outModels.getJSONObject(typeId.toString())
            assertTrue(m.getString("css").contains("🔊💡𠮟"))
            assertTrue(AnkiCss.isCurrent(m.getString("css"), cssBlock))
            val tmpl = m.getJSONArray("tmpls").getJSONObject(0)
            val qfmt = tmpl.getString("qfmt")
            val afmt = tmpl.getString("afmt")
            assertEquals(AnkiTemplate.installSafe(originalFront, loader), qfmt)
            assertEquals(AnkiTemplate.installSafe(originalBack, loader), afmt)
        }
    }

    @Test
    fun rejectsNonZipAndMissingCollectionWithKoreanMessage() {
        val garbage = tmp.newFile("bad.apkg").apply { writeText("not a zip") }
        val out = File(tmp.root, "nope.apkg")
        try {
            ApkgPatcher.patch(garbage, out, loader, cssBlock, emptySet(), emptySet(), 1764000000L, ::openJdbc)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("올바른 Anki 덱 파일"))
        }

        val emptyZip = tmp.newFile("empty.apkg")
        ZipOutputStream(FileOutputStream(emptyZip)).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("hi".toByteArray())
            zos.closeEntry()
        }
        try {
            ApkgPatcher.patch(emptyZip, out, loader, cssBlock, emptySet(), emptySet(), 1764000000L, ::openJdbc)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("올바른 Anki 덱 파일"))
        }
    }
}
