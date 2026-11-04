package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PartsTest {
    @Test
    fun parsesLine() {
        val p = Parts.parse("海\t85\t氵\tleft\t氵/水/r 毎//p\trM20.4,14.8c3.3,1.5|-M53,11c0.1,1.7|pM51.1,24c0.9,0.3\t悔")!!
        assertEquals(85, p.radical)
        assertEquals("氵", p.form)
        assertEquals("left", p.position)
        assertEquals(listOf(Parts.Part("氵", "水", true, false), Parts.Part("毎", "", false, true)), p.parts)
        assertEquals(listOf('r', '-', 'p'), p.strokes.map { it.tag })
        assertEquals("M20.4,14.8c3.3,1.5", p.strokes[0].path)
        assertEquals("悔", p.family)
        // A character with a radical number only (no KanjiVG strokes).
        val bare = Parts.parse("乶\t5\t\t\t\t\t")!!
        assertEquals(5, bare.radical)
        assertTrue(bare.parts.isEmpty() && bare.strokes.isEmpty())
        assertNull(Parts.parse(""))
    }

    @Test
    fun findsRadicalNames() {
        val all = Radical.parseAll("# comment\n9\t人\t\tひと\t사람 인\n9\t亻\t\tにんべん\t사람인변\t사람\n" +
            "9\t人\ttop\tひとやね\t사람인\t사람\n85\t水\t\tみず\t물 수\n85\t氵\t\tさんずい\t삼수변\t물\n")
        assertEquals(5, all.size)
        assertEquals("さんずい", Radical.find(all, 85, "氵", "left")!!.japanese)
        assertEquals("물", Radical.find(all, 85, "氵", "left")!!.meaning)
        assertEquals("ひとやね", Radical.find(all, 9, "人", "top")!!.japanese)
        assertEquals("ひと", Radical.find(all, 9, "人", "")!!.japanese)
        // A shape the table does not know falls back to the radical's main line.
        assertEquals("水", Radical.find(all, 85, "氺", "bottom")!!.form)
        assertEquals("水", Radical.find(all, 85, "", "")!!.form)
        assertNull(Radical.find(all, 3, "丶", ""))
    }

    @Test
    fun parsesOrigins() {
        val o = Origin.parse("형성: 氵(물)가 뜻, 毎(マイ)가 소리.\t물(氵)이 매번(毎) 밀려오는 곳 → 바다")!!
        assertEquals("형성", o.type)
        assertEquals("물(氵)이 매번(毎) 밀려오는 곳 → 바다", o.memo)
        assertEquals("회의 겸 형성", Origin.parse("회의 겸 형성: 亻+動\t")!!.type)
        assertEquals("", Origin.parse("山 모양")!!.type)
        assertEquals("", Origin.parse("상형: 산\t")!!.memo)
        assertNull(Origin.parse("\t"))
    }

    @Test
    fun unvoicedReadings() {
        assertEquals("ハン", Parts.unvoiced("バン"))
        assertEquals("ホウ", Parts.unvoiced("ポウ"))
        assertEquals("セイ", Parts.unvoiced("セイ"))
    }

    @Test
    fun parsesSvgPaths() {
        val segs = SvgPath.parse("M20.38,14.75c3.31,1.47,8.54,6.05,9.37,8.34")
        assertEquals(2, segs.size)
        assertEquals(SvgPath.Seg.Move(20.38f, 14.75f), segs[0])
        val c = segs[1] as SvgPath.Seg.Cubic
        assertEquals(23.69f, c.x1, 1e-3f)
        assertEquals(16.22f, c.y1, 1e-3f)
        assertEquals(29.75f, c.x, 1e-3f)
        assertEquals(23.09f, c.y, 1e-3f)
        // Numbers without commas ("1-0.1", "1.5.5"), implicit repeats, and S reflecting the last control point.
        val more = SvgPath.parse("M10,10c0,1-0.1,1.9-0.6,2c1.5.5,1,1,2,2s1,1,3,3")
        assertEquals(4, more.size)
        val c1 = more[1] as SvgPath.Seg.Cubic
        assertEquals(9.9f, c1.x2, 1e-3f)
        assertEquals(9.4f, c1.x, 1e-3f)
        assertEquals(12f, c1.y, 1e-3f)
        val c2 = more[2] as SvgPath.Seg.Cubic
        assertEquals(10.9f, c2.x1, 1e-3f)
        assertEquals(12.5f, c2.y1, 1e-3f)
        val s = more[3] as SvgPath.Seg.Cubic
        // Reflection of (10.4, 13) about (11.4, 14) is (12.4, 15).
        assertEquals(12.4f, s.x1, 1e-3f)
        assertEquals(15f, s.y1, 1e-3f)
        assertEquals(14.4f, s.x, 1e-3f)
        assertEquals(17f, s.y, 1e-3f)
        val abs = SvgPath.parse("M1,2C3,4,5,6,7,8S9,10,11,12L1,1z")
        assertEquals(SvgPath.Seg.Cubic(9f, 10f, 9f, 10f, 11f, 12f), abs[2])
        assertEquals(SvgPath.Seg.Line(1f, 1f), abs[3])
        assertEquals(SvgPath.Seg.Close, abs[4])
        assertTrue(SvgPath.parse("").isEmpty())
        assertTrue(SvgPath.parse("M1").isEmpty())
    }

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.exists() }

    /** The shipped tables: every 상용한자 has its radical and strokes, the radical named in Japanese and Korean. */
    @Test
    fun shippedDataHasRadicalsAndStrokes() {
        val file = asset("parts.tsv")
        val tsv = SortedTsv(file.readBytes())
        fun p(ch: String) = Parts.parse(ch + "\t" + tsv.find(ch)!!)!!
        val radicals = Radical.parseAll(asset("radicals.tsv").readText())
        assertEquals((1..214).toSet(), radicals.map { it.number }.toSet())

        val sea = p("海")
        assertEquals(85, sea.radical)
        assertEquals("氵", sea.form)
        assertEquals(listOf('r', 'r', 'r'), sea.strokes.take(3).map { it.tag })
        assertEquals(9, sea.strokes.size)
        assertTrue(sea.parts.any { it.element == "毎" && it.sound })
        val name = Radical.find(radicals, sea.radical, sea.form, sea.position)!!
        assertEquals("さんずい", name.japanese)
        assertEquals("삼수변", name.korean)
        // Japanese dictionaries' radicals: 来 under 木, 旧 under 日; 働 → にんべん; 道 → しんにょう.
        assertEquals(75, p("来").radical)
        assertEquals(72, p("旧").radical)
        assertEquals("にんべん", p("働").let { Radical.find(radicals, it.radical, it.form, it.position)!!.japanese })
        assertEquals("しんにょう", p("道").let { Radical.find(radicals, it.radical, it.form, it.position)!!.japanese })
        assertEquals("青", p("晴").family.take(1))

        // Sorted by UTF-8 bytes (the binary search depends on it), and every stroke path parses.
        val lines = file.readLines()
        val keys = lines.map { it.substringBefore('\t').toByteArray(Charsets.UTF_8) }
        for (i in 1 until keys.size) assertTrue(lines[i], compareBytes(keys[i - 1], keys[i]) < 0)
        val kanji = SortedTsv(asset("kanji.tsv").readBytes())
        var joyo = 0
        for (line in lines) {
            val ch = line.substringBefore('\t')
            val parts = Parts.parse(line)!!
            for (s in parts.strokes) assertTrue(ch, SvgPath.parse(s.path).firstOrNull() is SvgPath.Seg.Move)
            if (parts.radical != 0) assertNotNull(ch, Radical.find(radicals, parts.radical, parts.form, parts.position))
            val k = Kanji.parse(ch + "\t" + (kanji.find(ch) ?: continue)) ?: continue
            if (k.grade in 1..8) {
                joyo++
                assertTrue(ch, parts.radical in 1..214 && parts.strokes.isNotEmpty())
            }
        }
        assertEquals(2136, joyo)
    }

    /** 자원 and 외우기 for every 상용한자, short enough for the card. */
    @Test
    fun shippedOrigins() {
        val origin = asset("origin.tsv")
        val tsv = SortedTsv(origin.readBytes())
        val sea = Origin.parse(tsv.find("海")!!)!!
        assertEquals("형성", sea.type)
        val types = setOf("상형", "지사", "회의", "형성", "회의 겸 형성", "가차", "국자", "생략형", "불명")
        val lines = origin.readLines()
        val keys = lines.map { it.substringBefore('\t').toByteArray(Charsets.UTF_8) }
        for (i in 1 until keys.size) assertTrue(lines[i], compareBytes(keys[i - 1], keys[i]) < 0)
        for (line in lines) {
            val o = Origin.parse(line.substringAfter('\t'))!!
            assertTrue(line, o.type in types)
            assertTrue(line, o.origin.length <= 90 && o.memo.length <= 50)
        }
        val kanji = asset("kanji.tsv").readLines().map { it.split('\t') }
            .filter { it.size > 6 && it[6].toIntOrNull() in 1..8 }.map { it[0] }
        assertEquals(2136, kanji.size)
        assertEquals(emptyList<String>(), kanji.filter { tsv.find(it) == null })
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
