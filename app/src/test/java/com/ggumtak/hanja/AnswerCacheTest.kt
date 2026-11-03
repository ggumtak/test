package com.ggumtak.hanja

import org.junit.Assert.*
import org.junit.Test

class AnswerCacheTest {
    @Test fun keysAreBoundedVersionedAndUnambiguous() {
        assertEquals(67, AnswerCache.key("v5", "案", "長い文".repeat(1000)).length)
        assertNotEquals(AnswerCache.key("v5", "a", "bc"), AnswerCache.key("v5", "ab", "c"))
        assertNotEquals(AnswerCache.key("v5", "案", "文"), AnswerCache.key("v6", "案", "文"))
        assertEquals(AnswerCache.key("v5", "案", "文"), AnswerCache.key("v5", "案", "文"))
    }

    @Test fun preservesTheAnswerAndEvictsOnlyTheOldestBatch() {
        assertEquals("{\"a\":1}", AnswerCache.answer(AnswerCache.pack("{\"a\":1}", 42)))
        assertNull(AnswerCache.answer("old answer"))
        val entries = (0..9).associate { "card$it" to AnswerCache.pack("{}", it.toLong()) }
        assertEquals(listOf("card0"), AnswerCache.evictions(entries, 10))
        assertTrue(AnswerCache.evictions(entries, 11).isEmpty())
        assertEquals(listOf("old", "card0"), AnswerCache.evictions(entries + ("old" to "legacy"), 10))
    }
}
