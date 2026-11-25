package com.ggumtak.hanja

import com.ggumtak.hanja.update.ApkPatch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** The fixtures are made by tools/apk_patch.py: the app must rebuild exactly what the tool encoded. */
class ApkPatchTest {
    private val dir = File("src/test/resources/patch")
    private val old = File(dir, "old.zip")
    private val patch = File(dir, "new.patch").readBytes()

    @Test
    fun rebuildsTheNewApkByteForByte() {
        val out = File.createTempFile("new", ".apk")
        assertTrue(ApkPatch.apply(old, ByteArrayInputStream(patch), out))
        assertArrayEquals(File(dir, "new.zip").readBytes(), out.readBytes())
        out.delete()
    }

    @Test
    fun refusesAnotherBaseOrACutPatch() {
        val out = File.createTempFile("new", ".apk")
        assertFalse(ApkPatch.apply(File(dir, "new.zip"), ByteArrayInputStream(patch), out))
        assertFalse(out.exists())
        assertFalse(ApkPatch.apply(old, ByteArrayInputStream(patch.copyOf(patch.size - 20)), out))
        assertFalse(out.exists())
        assertFalse(ApkPatch.apply(old, ByteArrayInputStream("not a patch".toByteArray()), out))
    }
}
