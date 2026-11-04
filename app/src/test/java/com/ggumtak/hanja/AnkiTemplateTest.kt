package com.ggumtak.hanja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnkiTemplateTest {
    private val code = File("src/main/assets/anki-tap.html").readText()
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
}
