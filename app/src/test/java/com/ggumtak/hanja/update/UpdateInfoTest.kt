package com.ggumtak.hanja.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {
    @Test
    fun readsTheNewestApkOfTheRelease() {
        val json = """{"tag_name":"latest","assets":[
            {"name":"Hanja-23.apk","size":100,"browser_download_url":"https://github.com/ggumtak/test/releases/download/latest/Hanja-23.apk"},
            {"name":"notes.txt","size":5,"browser_download_url":"https://example.com/notes.txt"},
            {"name":"Hanja-24.apk","size":6950056,"browser_download_url":"https://github.com/ggumtak/test/releases/download/latest/Hanja-24.apk"}]}"""
        val l = UpdateInfo.parse(json)!!
        assertEquals(24, l.build)
        assertEquals(6950056L, l.size)
        assertTrue(l.url.endsWith("Hanja-24.apk"))
        assertTrue(UpdateInfo.isNewer(l, 23))
        assertFalse(UpdateInfo.isNewer(l, 24))
    }

    @Test
    fun findsThePatchFromTheInstalledBuild() {
        val base = "https://github.com/ggumtak/test/releases/download/latest/"
        val json = """{"assets":[
            {"name":"Hanja-256.apk","size":25000000,"browser_download_url":"${base}Hanja-256.apk"},
            {"name":"Hanja-256.from-255.patch","size":130000,"browser_download_url":"${base}Hanja-256.from-255.patch"},
            {"name":"Hanja-256.from-250.patch","size":20000000,"browser_download_url":"${base}Hanja-256.from-250.patch"},
            {"name":"Hanja-255.from-254.patch","size":1000,"browser_download_url":"${base}Hanja-255.from-254.patch"}]}"""
        val l = UpdateInfo.parse(json)!!
        assertEquals(256, l.build)
        assertEquals(setOf(255, 250), l.patches.keys)
        assertTrue(UpdateInfo.patchFor(l, 255)!!.url.endsWith("Hanja-256.from-255.patch"))
        // Not worth it (most of the APK), or none for this build: the whole APK.
        assertNull(UpdateInfo.patchFor(l, 250))
        assertNull(UpdateInfo.patchFor(l, 254))
        assertEquals("0.1MB", UpdateInfo.sizeText(130000))
        assertEquals("24MB", UpdateInfo.sizeText(25000000))
    }

    @Test
    fun ignoresAnythingElse() {
        assertNull(UpdateInfo.parse("not json"))
        assertNull(UpdateInfo.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateInfo.parse("""{"assets":[{"name":"Hanja-x.apk","browser_download_url":"https://a/b"}]}"""))
        assertNull(UpdateInfo.parse("""{"assets":[{"name":"Hanja-3.apk","browser_download_url":"http://insecure/b"}]}"""))
    }

    @Test
    fun texts() {
        assertEquals("Hanja-24.apk", UpdateInfo.fileName(24))
        assertEquals("내려받는 중… 40%", UpdateInfo.progressText(40))
        assertEquals("내려받는 중…", UpdateInfo.progressText(-1))
    }
}
