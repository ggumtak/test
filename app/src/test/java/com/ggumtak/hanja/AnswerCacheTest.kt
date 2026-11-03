package com.ggumtak.hanja

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnswerCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun keysAreBoundedVersionedAndUnambiguous() {
        assertEquals(67, AnswerCache.key("v5", "案", "長い文".repeat(1000)).length)
        assertNotEquals(AnswerCache.key("v5", "a", "bc"), AnswerCache.key("v5", "ab", "c"))
        assertNotEquals(AnswerCache.key("v5", "案", "文"), AnswerCache.key("v6", "案", "文"))
        assertEquals(AnswerCache.key("v5", "案", "文"), AnswerCache.key("v5", "案", "文"))
    }

    @Test fun readsTheLegacyPreferencesFormat() {
        assertEquals("{\"a\":1}" to 42L, AnswerCache.legacy("""{"time":42,"answer":"{\"a\":1}"}"""))
        assertNull(AnswerCache.legacy("old answer"))
        assertNull(AnswerCache.legacy(7))
    }

    @Test fun storesOneFilePerAnswer() {
        val dir = File(tmp.root, "answers/readings")
        val store = AnswerStore(dir, 10)
        val key = AnswerCache.key("reading-v5", "今日", "今日は雨")
        assertNull(store.get(key))
        store.put(key, "{\"word\":\"今日\"}")
        assertEquals("{\"word\":\"今日\"}", store.get(key))
        store.put(key, "{\"word\":\"今日\",\"x\":1}")
        assertEquals("{\"word\":\"今日\",\"x\":1}", store.get(key))
        assertEquals(1, store.size())
        assertFalse(dir.list()!!.any { it.contains(':') || it.endsWith(".tmp") })
    }

    @Test fun evictsTheLeastRecentlyUsedTenth() {
        val store = AnswerStore(tmp.root, 10)
        val now = System.currentTimeMillis()
        for (i in 0..9) store.put("card$i", "{}", now - (100 - i) * 1000L)
        // A hit keeps the oldest card: it now counts as just used.
        assertEquals("{}", store.get("card0"))
        // Over the limit: the oldest tenth (with the new one, 11 - 10 + 1 = 2 files) goes.
        store.put("card10", "{}")
        assertEquals(9, store.size())
        assertNotNull(store.get("card0"))
        assertNull(store.get("card1"))
        assertNull(store.get("card2"))
        assertNotNull(store.get("card10"))
    }

    @Test fun movesManyInBeforeTrimmingOnce() {
        val store = AnswerStore(tmp.root, 10)
        for (i in 0..19) store.put("old$i", "{}", 1_000_000L + i * 1000, evict = false)
        assertEquals(20, store.size())
        store.trim()
        assertEquals(9, store.size())
        assertNotNull(store.get("old19"))
        assertNull(store.get("old0"))
    }
}
