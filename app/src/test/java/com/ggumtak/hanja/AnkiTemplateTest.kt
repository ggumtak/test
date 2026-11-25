package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnkiTemplateTest {
    private val code = File("src/main/assets/anki-loader.html").readText()
    private val v1 = "<!-- 한자 바로보기: 한자·가타카나를 탭하면 카드가 뜹니다 -->\n<style>a.hjtap{}</style>\n<script>\n(function(){})();\n</script>"
    private val v3 = "<!-- 한자 바로보기 v3 (AnkiDroid): 붙여 넣으세요. -->\n<style>\na.hjtap{}\n</style>\n<script>\nwindow.__hjtap3();\n</script>\n"

    private fun type(id: Long, name: String, vararg sides: Pair<String, String>) =
        AnkiTemplate.NoteType(id, name, sides.mapIndexed { i, s -> AnkiTemplate.Template(i, "카드 ${i + 1}", s.first, s.second) })

    @Test
    fun assetIsOneBlock() {
        assertTrue(code.trimStart().startsWith(AnkiTemplate.MARK))
        assertEquals(1, Regex(Regex.escape("</script>")).findAll(code).count())
        assertEquals("", AnkiTemplate.strip(code)!!.trim())
    }

    @Test
    fun replacesOldVersionsAndKeepsCardContent() {
        val front = "{{문장}}\n<div>{{뜻}}</div>\n\n$v3"
        val out = AnkiTemplate.install(front, code)
        assertEquals("{{문장}}\n<div>{{뜻}}</div>\n\n" + code.trim() + "\n", out)
        assertTrue(AnkiTemplate.isCurrent(out, code))
        // Two pasted versions (v1 then v3) collapse into the current one; text between them survives.
        val twice = AnkiTemplate.install("{{a}}\n$v1\n{{b}}\n$v3", code)
        assertEquals("{{a}}\n{{b}}\n\n" + code.trim() + "\n", twice)
        assertEquals(1, twice.split(AnkiTemplate.MARK).size - 1)
    }

    @Test
    fun cutOffOrUnmarkedCodeIsLeftAlone() {
        assertTrue(AnkiTemplate.broken("{{a}}\n<!-- 한자 바로보기 v3 -->\n<script>\nhalf"))
        assertTrue(AnkiTemplate.broken("{{a}}\n<style>a.hjtap{}</style><script></script>"))
        assertFalse(AnkiTemplate.broken("{{a}}\n$v3"))
        val t = type(1, "문법", "{{a}}\n<script>hjtap</script>" to "{{FrontSide}}\n$v3")
        val plan = AnkiTemplate.refresh(listOf(t), code)
        assertEquals(listOf("문법"), plan.broken)
        assertTrue(plan.changes.isEmpty())
    }

    @Test
    fun refreshOnlyTouchesInstalledNoteTypes() {
        val grammar = type(1, "문법", "{{문장}}\n$v3" to "{{FrontSide}}\n<hr id=answer>\n{{뜻}}\n$v3")
        val basic = type(2, "Basic", "{{Front}}" to "{{FrontSide}}<hr id=answer>{{Back}}")
        val plan = AnkiTemplate.refresh(listOf(grammar, basic), code)
        assertEquals(1, plan.changes.size)
        val c = plan.changes[0]
        assertEquals(1L, c.typeId)
        assertTrue(AnkiTemplate.isCurrent(c.front!!, code))
        assertTrue(c.back!!.startsWith("{{FrontSide}}\n<hr id=answer>\n{{뜻}}\n\n"))
        // Once current, nothing more to write.
        val done = type(1, "문법", c.front!! to c.back!!)
        assertTrue(done.current(code))
        assertTrue(AnkiTemplate.refresh(listOf(done, basic), code).changes.isEmpty())
    }

    @Test
    fun chosenNoteTypeGetsTheCodeBackAfterADeckUpdate() {
        // The deck publisher replaced the templates: the block is gone, but the user chose this note type.
        val replaced = type(3, "JLPT MAX덱 어휘", "{{Word}}" to "{{FrontSide}}<hr id=answer>{{Meaning}}")
        val other = type(4, "Basic", "{{Front}}" to "{{Back}}")
        assertTrue(AnkiTemplate.refresh(listOf(replaced, other), code).changes.isEmpty())
        val plan = AnkiTemplate.refresh(listOf(replaced, other), code, chosen = setOf(3L))
        assertEquals(listOf(3L), plan.changes.map { it.typeId })
        assertTrue(AnkiTemplate.isCurrent(plan.changes[0].front!!, code) && AnkiTemplate.isCurrent(plan.changes[0].back!!, code))
        // A note type with the block counts as chosen; a deleted one drops out.
        val grammar = type(1, "문법", "{{a}}\n$v3" to "{{b}}")
        assertEquals(setOf(1L, 3L), AnkiTemplate.keep(listOf(grammar, replaced, other), setOf(3L, 99L)))
    }

    @Test
    fun writesThatAnkiDroppedAreReported() {
        val before = type(3, "JLPT MAX덱 어휘", "{{Word}}" to "{{FrontSide}}<hr id=answer>{{Meaning}}")
        val plan = AnkiTemplate.plan(listOf(before), setOf(3L), code)
        val c = plan.changes.single()
        // Both sides are in Anki: nothing missed.
        assertTrue(AnkiTemplate.missed(plan, listOf(type(3, "JLPT MAX덱 어휘", c.front!! to c.back!!)), code).isEmpty())
        // The front went in, the back did not; nothing went in; the note type is gone.
        assertEquals(listOf(c), AnkiTemplate.missed(plan, listOf(type(3, "JLPT MAX덱 어휘", c.front!! to before.templates[0].back)), code))
        assertEquals(listOf(c), AnkiTemplate.missed(plan, listOf(before), code))
        assertEquals(listOf(c), AnkiTemplate.missed(plan, emptyList(), code))
        // Taking the code out: missed while the block is still there.
        val on = type(3, "JLPT MAX덱 어휘", c.front!! to c.back!!)
        val off = AnkiTemplate.plan(listOf(on), emptySet(), code)
        assertEquals(1, AnkiTemplate.missed(off, listOf(on), code).size)
        assertTrue(AnkiTemplate.missed(off, listOf(before), code).isEmpty())
    }

    @Test
    fun choosingNoteTypesInstallsAndRemoves() {
        val grammar = type(1, "문법", "{{문장}}\n$v3" to "{{FrontSide}}\n$v3")
        val words = type(2, "단어", "{{단어}}" to "{{FrontSide}}", "{{뜻}}" to "{{FrontSide}}")
        val plan = AnkiTemplate.plan(listOf(grammar, words), setOf(2L), code)
        val byType = plan.changes.groupBy { it.typeId }
        assertEquals(listOf("{{문장}}"), byType[1L]!!.map { it.front })
        assertEquals("{{FrontSide}}", byType[1L]!![0].back)
        assertEquals(2, byType[2L]!!.size)
        assertTrue(byType[2L]!!.all { AnkiTemplate.isCurrent(it.front!!, code) && AnkiTemplate.isCurrent(it.back!!, code) })
        assertNull(AnkiTemplate.plan(listOf(words), emptySet(), code).changes.firstOrNull())
    }

    @Test
    fun largeTemplatesAreLeftForOnePasteByHand() {
        val big = "<div>" + "あ".repeat(AnkiTemplate.WRITE_LIMIT) + "</div>"
        val t = type(9, "JLPT MAX덱 문법", big to big)
        val plan = AnkiTemplate.plan(listOf(t), setOf(9L), code)
        // Never sent (AnkiDroid would refuse it), listed for the user instead.
        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf(AnkiTemplate.Manual(9, "JLPT MAX덱 문법", "카드 1")), plan.manual)
        // Pasted at the top, an old block left further down: done, nothing more to do or to say.
        val pasted = code + "\n" + big + "\n" + v3
        val after = AnkiTemplate.plan(listOf(type(9, "JLPT MAX덱 문법", pasted to pasted)), setOf(9L), code)
        assertTrue(after.changes.isEmpty())
        assertTrue(after.manual.isEmpty())
        // One side small enough: that side is still written by the app.
        val mixed = AnkiTemplate.plan(listOf(type(9, "JLPT MAX덱 문법", "{{a}}" to big)), setOf(9L), code)
        assertEquals(1, mixed.changes.size)
        assertTrue(mixed.changes[0].back == null && mixed.changes[0].front != null)
        assertEquals(1, mixed.manual.size)
    }

    @Test
    fun crlfAndInstallSafeWorkAcrossCleanOldAndBrokenTemplates() {
        val crlfLoader = code.trim().replace("\n", "\r\n")
        val bigCrlf = crlfLoader + "\r\n<div>" + "あ".repeat(AnkiTemplate.WRITE_LIMIT) + "</div>"
        val plan = AnkiTemplate.plan(listOf(type(9, "JLPT MAX덱 어휘", bigCrlf to bigCrlf)), setOf(9L), code)
        assertTrue(plan.changes.isEmpty())
        assertTrue(plan.manual.isEmpty())
        assertTrue(AnkiTemplate.isCurrent("{{Word}}\r\n\r\n$crlfLoader\r\n", code))

        // installSafe cleanly replaces old versions and leaves already-current templates untouched.
        val replaced = AnkiTemplate.installSafe("{{Word}}\n$v3", code)
        assertTrue(AnkiTemplate.isCurrent(replaced, code))
        assertFalse(replaced.contains("__hjtap3"))
        assertEquals(replaced, AnkiTemplate.installSafe(replaced, code))

        // Broken partial paste: prepends loader once without deleting deck HTML.
        val broken = "<div>{{Word}}</div>\n<!-- 한자 바로보기 v3 -->\n<script>cut"
        val recovered = AnkiTemplate.installSafe(broken, code)
        assertTrue(recovered.startsWith(code.trim()))
        assertTrue(recovered.contains("<div>{{Word}}</div>"))
        assertEquals(recovered, AnkiTemplate.installSafe(recovered, code))
    }
}
