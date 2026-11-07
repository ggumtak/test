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
        assertTrue(!p.filled && p.starts.isEmpty())
        // Brush outlines with where each stroke starts.
        val brush = Parts.parse("川\t47\t川\t\t\t-M1,2z|-M3,4z|-M5,6z\t\tf\t10,20;30,40;50,60")!!
        assertTrue(brush.filled)
        assertEquals(listOf(10f to 20f, 30f to 40f, 50f to 60f), brush.starts)
        assertEquals(emptyList<Pair<Float, Float>>(), Parts.parseStarts("10,x"))
        // A character with a radical number only (no KanjiVG strokes).
        val bare = Parts.parse("乶\t5\t\t\t\t\t")!!
        assertEquals(5, bare.radical)
        assertTrue(bare.parts.isEmpty() && bare.strokes.isEmpty())
        assertNull(Parts.parse(""))
    }

    @Test
    fun partRolesFollowTheDrawing() {
        // R: holds the radical without being it; P: holds the sound part, named after the slash; the brush's centre
        // lines in the tenth column.
        val p = Parts.parse("昼\t72\t日\t\t尺// 旦//R\t-M1,2z|rM3,4z\t\tf\t1,2;3,4\t1,2,5,6|3,4")!!
        assertEquals(Parts.Part("旦", "", false, false, radicalInside = true), p.parts[1])
        assertEquals(listOf(listOf(1f, 2f, 5f, 6f), listOf(3f, 4f)), p.medians.map { it.toList() })
        val q = Parts.parse("塗\t32\t土\tbottom\t涂//P/余 土//r\t\t")!!
        assertEquals(Parts.Part("涂", "", false, false, soundInside = true, inner = "余"), q.parts[0])
        assertTrue(q.medians.isEmpty())
        // Centre lines that are not one per stroke, or malformed, are dropped (the strokes then fade in).
        assertTrue(Parts.parseMedians("1,2|3,4", 3).isEmpty())
        assertTrue(Parts.parseMedians("1,2,3", 1).isEmpty())
        assertTrue(Parts.parseMedians("1,x", 1).isEmpty())
        // The radical inside the sound part (修: 亻 in 攸) or the sound part itself (到: 刂) files the kanji only.
        assertTrue(Parts.parse("修\t9\t亻\tleft\t攸//pR 彡//\t\t")!!.radicalInSound)
        assertTrue(Parts.parse("到\t18\t刂\tright\t至// 刂/刀/rp\t\t")!!.radicalInSound)
        assertTrue(!Parts.parse("海\t85\t氵\tleft\t氵/水/r 毎//p\t\t")!!.radicalInSound)
        assertEquals("毎", Parts.parse("海\t85\t氵\tleft\t氵/水/r 毎//p\t\t")!!.soundElement)
        assertEquals("", Parts.parse("診\t149\t言\tleft\t言//r 人//p 彡//p\t\t")!!.soundElement)
    }

    @Test
    fun strokeCountFollowsTheDrawing() {
        assertEquals("13획 · 사전에 따라 12획", Parts.strokeLabel(12, 13))
        assertEquals("9획", Parts.strokeLabel(9, 9))
        assertEquals("5획", Parts.strokeLabel(5, 0))
        assertEquals("4획", Parts.strokeLabel(0, 4))
        assertEquals("", Parts.strokeLabel(0, 0))
    }

    @Test
    fun extendsTheCentreLine() {
        // A horizontal stroke from (100, 500) to (300, 500) through (200, 500): pushed out 10 at each end.
        val out = StrokeOrder.extendLine(floatArrayOf(100f, 500f, 200f, 500f, 300f, 500f), 10f)
        assertEquals(listOf(90f, 500f, 200f, 500f, 310f, 500f), out.toList())
        assertEquals(listOf(5f, 5f), StrokeOrder.extendLine(floatArrayOf(5f, 5f), 10f).toList())
        assertEquals(listOf(5f, 5f, 5f, 5f), StrokeOrder.extendLine(floatArrayOf(5f, 5f, 5f, 5f), 10f).toList())
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
        assertEquals("뜻+소리", o.label)
        assertEquals("氵(물)가 뜻, 毎(マイ)가 소리.", o.body)
        assertEquals("물(氵)이 매번(毎) 밀려오는 곳 → 바다", o.memo)
        assertEquals("뜻+뜻", Origin.parse("회의: 亻+木\t")!!.label)
        assertEquals("뜻+소리", Origin.parse("회의 겸 형성: 亻+動\t")!!.label)
        assertEquals("山 모양", Origin.parse("山 모양")!!.body)
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
        // The brush outlines: relative quadratics, T reflecting the last control point, z back to the start.
        val brush = SvgPath.parse("M100,200q10,20,30,40t10,10c1,1,2,2,3,3z")
        assertEquals(SvgPath.Seg.Quad(110f, 220f, 130f, 240f), brush[1])
        // T's control point: (130, 240) reflected about itself from (110, 220) is (150, 260).
        assertEquals(SvgPath.Seg.Quad(150f, 260f, 140f, 250f), brush[2])
        assertEquals(SvgPath.Seg.Cubic(141f, 251f, 142f, 252f, 143f, 253f), brush[3])
        assertEquals(SvgPath.Seg.Close, brush[4])
    }

    @Test
    fun strokeNumbersDoNotOverlap() {
        // 宀's second and third strokes start at the same point: the third number moves aside, inside the box.
        val placed = StrokeOrder.placeLabels(listOf(500f to 100f, 200f to 300f, 200f to 300f, 1020f to 1020f), 46f, 1024f)
        assertEquals(500f to 100f, placed[0])
        assertEquals(200f to 300f, placed[1])
        val (x, y) = placed[2]
        assertTrue(Math.hypot((x - 200f).toDouble(), (y - 300f).toDouble()) >= 46 * 1.9)
        assertEquals(978f to 978f, placed[3])
        // Every pair of labels apart for a real kanji (室: 9 strokes, two pairs starting together).
        val tsv = SortedTsv(asset("parts.tsv").readBytes())
        val room = Parts.parse("室\t" + tsv.find("室")!!)!!
        val labels = StrokeOrder.placeLabels(room.starts, 1024f * 0.045f, 1024f)
        for (i in labels.indices) for (j in 0 until i) {
            val d = Math.hypot((labels[i].first - labels[j].first).toDouble(), (labels[i].second - labels[j].second).toDouble())
            assertTrue("室 $i $j", d >= 1024 * 0.045 * 1.9 - 0.01)
        }
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
        // Brush outlines (AnimCJK) for the 상용한자, closed shapes inside the 1024 box, numbered where they start.
        assertTrue(sea.filled)
        assertEquals(sea.strokes.size, sea.starts.size)
        assertTrue(sea.starts.all { (x, y) -> x in 0f..1024f && y in 0f..1024f })
        val first = SvgPath.parse(sea.strokes[0].path)
        assertTrue(first.last() is SvgPath.Seg.Close)
        val start = first[0] as SvgPath.Seg.Move
        assertTrue(start.x in 0f..1024f && start.y in 0f..1024f)
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
        // A part only holding the radical keeps its own name (旦 holds 日, 音 holds 立, 由 holds 田).
        assertEquals(Parts.Part("旦", "", false, false, radicalInside = true), p("昼").parts[1])
        assertTrue(p("章").parts.single { it.element == "音" }.let { it.radicalInside && !it.radical })
        assertTrue(p("画").parts.single { it.element == "由" }.let { it.radicalInside && !it.radical })
        // The 구성 line names the sound part the drawing paints blue (低: 氐, made of 氏 and 一; 新: 立).
        assertEquals(Parts.Part("氐", "氏+一", false, true), p("低").parts[1])
        assertEquals("底邸抵", p("低").family)
        assertTrue(p("新").parts.single { it.element == "立" }.sound)
        // 到's 刂 is both the radical and the sound part.
        assertTrue(p("到").parts.single { it.element == "刂" }.let { it.radical && it.sound })

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
                assertTrue(ch, parts.radical in 1..214 && parts.strokes.isNotEmpty() && parts.filled &&
                    parts.starts.size == parts.strokes.size && parts.medians.size == parts.strokes.size)
                // Every centre line starts where the stroke's number goes, inside the box.
                for ((m, st) in parts.medians.zip(parts.starts)) {
                    assertTrue(ch, m.all { it in -1f..1025f })
                    assertEquals(ch, st, m[0] to m[1])
                }
                // Blue strokes are named in the 구성 line (a part that is, or holds, the sound part) when it has one.
                val blue = parts.strokes.any { it.tag == Parts.SOUND }
                if (blue && parts.parts.isNotEmpty() && ch !in "従") assertTrue(ch, parts.parts.any { it.sound || it.soundInside })
                if (parts.parts.any { it.sound && !it.radical }) assertTrue(ch, blue)
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
        // In plain Korean: the learner is no hanja specialist, so no technical term in the body (the type itself is
        // shown in plain words by Origin.label).
        val jargon = listOf("신자체", "정자", "說文", "설문", "갑골", "금문", "본자", "이체", "속자", "가차", "생략형",
            "회의", "형성", "상형", "지사", "소리 겸 뜻", "설이 갈림")
        for (line in lines) {
            val o = Origin.parse(line.substringAfter('\t'))!!
            assertTrue(line, o.type in types && o.label != o.type)
            assertTrue(line, o.origin.length <= 120 && o.memo.length <= 50)
            assertEquals(line, emptyList<String>(), jargon.filter { it in o.body })
        }
        val kanji = asset("kanji.tsv").readLines().map { it.split('\t') }
            .filter { it.size > 6 && it[6].toIntOrNull() in 1..8 }.map { it[0] }
        assertEquals(2136, kanji.size)
        assertEquals(emptyList<String>(), kanji.filter { tsv.find(it) == null })
        // The 구성 line written to match the 유래: it reads, and its sound parts are the drawing's blue ones.
        val parts = SortedTsv(asset("parts.tsv").readBytes())
        var written = 0
        for (ch in kanji) {
            val o = Origin.parse(tsv.find(ch)!!)!!
            if (o.kosei.isEmpty()) continue
            written++
            val units = Kosei.parse(o.kosei)
            assertNotNull(ch, units)
            if (o.kosei.startsWith("!")) continue
            val p = Parts.parse(ch + "\t" + parts.find(ch)!!)!!
            if (units!!.any { it.sound }) assertTrue(ch, p.parts.any { it.sound || it.soundInside })
            if (p.parts.any { it.sound }) assertTrue(ch, units.any { it.sound })
            assertTrue(ch, units.count { it.radical } <= 1 || p.parts.count { it.radical } > 1 ||
                p.parts.any { it.radical && it.original.contains('+') })
            for (u in units) assertTrue(ch, u.role in setOf("", "뜻", "소리", "뜻·소리", "모양", "기호") && u.note.length <= 16)
        }
        assertTrue(written >= 853)
    }

    @Test
    fun parsesKosei() {
        val u = Kosei.parse("聿[뜻](붓) + ^曰[소리 シャ](者를 줄인 꼴)")!!
        assertEquals(Kosei.Unit("聿", "뜻", "", "붓", radical = false, holdsRadical = false), u[0])
        assertEquals(Kosei.Unit("曰", "소리", "シャ", "者를 줄인 꼴", radical = true, holdsRadical = false), u[1])
        assertTrue(u[1].sound && !u[0].sound)
        val g = Kosei.parse("~牛+巾[뜻](나무 未가 변한 꼴) + 刂")!!
        assertEquals(listOf("牛", "巾"), g[0].pieces)
        assertTrue(g[0].holdsRadical && g[1].role.isEmpty() && g[1].note.isEmpty())
        assertEquals(2, Kosei.parse("!音[뜻](음악) + 十[뜻](마침)")!!.size)
        assertNull(Kosei.parse(""))
        assertNull(Kosei.parse("聿[뜻](붓(筆))"))
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
