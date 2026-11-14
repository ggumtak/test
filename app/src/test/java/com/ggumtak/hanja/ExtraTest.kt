package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** assets/extra.tsv (tools/extra): the lines written for radicals and parts outside the 상용한자. */
class ExtraTest {
    private val lines: List<String> by lazy { File("src/main/assets/extra.tsv").readLines().filter { it.isNotEmpty() } }

    private val joyo: Set<String> by lazy {
        File("src/main/assets/origin.tsv").readLines().filter { it.isNotEmpty() }.map { it.substringBefore('\t') }.toSet()
    }

    @Test
    fun fillsWhatTheDictionaryLacks() {
        val x = Extra.parse("기슭 엄, 굴바위 엄\t벼랑, 언덕\t상형: 깎아지른 벼랑의 모양을 그림.\t바위(厂) 밑에 비를 긋는 → 벼랑\t原反圧")!!
        // KANJIDIC's line keeps its readings; the 훈음 and 뜻 the card shows first come from the line written for it.
        val raw = Kanji.parse("厂\t언덕\t\tカン\tかりがね\t2\t\twild goose; trailing cliff radical (no. 27)\t\t\t")!!
        val k = x.fill("厂", raw)
        assertEquals("기슭 엄, 굴바위 엄", k.hunJa)
        assertEquals("벼랑, 언덕", k.gloss)
        assertEquals("カン", k.on)
        assertEquals(2, k.strokes)
        // A character KANJIDIC does not have still gets a card.
        val made = x.fill("㔾", null)
        assertEquals("㔾", made.ch)
        assertEquals("기슭 엄, 굴바위 엄", made.hunJa)
        assertEquals("", made.on)
        // What a 상용한자 line already says is never replaced.
        val own = raw.copy(gloss = "제 뜻", hunJa = "제 훈음")
        assertEquals(own, x.fill("厂", own))
        val o = x.asOrigin()!!
        assertEquals("상형", o.type)
        assertEquals("바위(厂) 밑에 비를 긋는 → 벼랑", o.memo)
        assertEquals(listOf("原", "反", "圧"), Similar.parse(x.used))
    }

    @Test
    fun aDashIsAnEmptyField() {
        val x = Extra.parse("-\t兎의 다른 꼴\t-\t-\t-")!!
        assertEquals("", x.hun)
        assertEquals("", x.used)
        assertNull(x.asOrigin())
        // No 훈음 written: the dictionary's own stays what the card falls back on.
        val raw = Kanji.parse("兔\t토끼 토\t\tト\tうさぎ\t8\t\trabbit\t\t\t")!!
        assertEquals("", x.fill("兔", raw).hunJa)
        assertEquals("토끼 토", x.fill("兔", raw).hun)
        assertNull(Extra.parse("훈음만"))
    }

    @Test
    fun theLinesAreWellFormed() {
        assertTrue(lines.isNotEmpty())
        val keys = lines.map { it.substringBefore('\t') }
        // Sorted by UTF-8 bytes, as the app's search needs (PackTsv refuses anything else too).
        val bytes = keys.map { it.toByteArray(Charsets.UTF_8) }
        for (i in 1 until bytes.size) {
            val a = bytes[i - 1]
            val b = bytes[i]
            var d = 0
            for (j in 0 until minOf(a.size, b.size)) {
                d = (a[j].toInt() and 0xFF) - (b[j].toInt() and 0xFF)
                if (d != 0) break
            }
            if (d == 0) d = a.size - b.size
            assertTrue(keys[i], d < 0)
        }
        val types = Origin.LABELS.keys
        for (line in lines) {
            val ch = line.substringBefore('\t')
            assertEquals(ch, 1, ch.codePointCount(0, ch.length))
            assertFalse("$ch is a 상용한자: its lines are in origin.tsv", ch in joyo)
            assertEquals(ch, 6, line.split('\t').size)
            val x = Extra.parse(line.substringAfter('\t'))
            assertNotNull(ch, x)
            x!!
            assertTrue(ch, x.gloss.isNotEmpty())
            for (item in x.hun.split(", ").filter { it.isNotEmpty() }) {
                // "훈 음": the Korean reading is the last syllable.
                assertTrue("$ch: $item", Regex("[가-힣]+( [가-힣]+)* [가-힣]").matches(item))
            }
            val o = x.asOrigin()
            assertNotNull(ch, o)
            assertTrue("$ch: ${o!!.type}", o.type in types)
            assertTrue(ch, x.origin.length <= 120)
            assertTrue(ch, " → " in x.memo && x.memo.length <= 50)
            val used = Similar.parse(x.used)
            assertTrue(ch, used.size <= 3 && used.all { it in joyo } && ch !in used)
        }
    }
}
