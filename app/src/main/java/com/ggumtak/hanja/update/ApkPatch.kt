package com.ggumtak.hanja.update

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/**
 * Rebuilds the new APK from the installed one and an update patch (tools/apk_patch.py, which documents the
 * format), so an update downloads only what changed. The result must be byte for byte the APK CI signed: its
 * size and SHA-256 are in the patch, and anything else (a patch for another build, a cut download) gives false and
 * the caller downloads the whole APK instead.
 */
object ApkPatch {
    private val MAGIC = "HJPATCH1".toByteArray(Charsets.US_ASCII)

    fun apply(old: File, patch: InputStream, out: File): Boolean {
        val ok = runCatching { rebuild(old, patch, out) }.getOrDefault(false)
        if (!ok) out.delete()
        return ok
    }

    private fun rebuild(old: File, patch: InputStream, out: File): Boolean {
        val head = ByteArray(MAGIC.size)
        if (patch.readNBytesCompat(head) != head.size || !head.contentEquals(MAGIC)) return false
        val body = DataInputStream(InflaterInputStream(patch).buffered(64 * 1024))
        val oldSize = body.readLong()
        val oldSha = ByteArray(32).also { body.readFully(it) }
        val newSize = body.readLong()
        val newSha = ByteArray(32).also { body.readFully(it) }
        if (old.length() != oldSize || !sha256(old).contentEquals(oldSha)) return false
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val buf = ByteArray(64 * 1024)
        RandomAccessFile(old, "r").use { src ->
            out.outputStream().buffered(64 * 1024).use { dst ->
                fun put(n: Int) {
                    written += n
                    if (written > newSize) throw IllegalStateException("too long")
                    digest.update(buf, 0, n)
                    dst.write(buf, 0, n)
                }
                loop@ while (true) {
                    when (body.readUnsignedByte()) {
                        'C'.code -> {
                            val off = body.readLong()
                            var left = body.readLong()
                            if (off < 0 || left < 0 || off + left > oldSize) return false
                            src.seek(off)
                            while (left > 0) {
                                val n = minOf(left, buf.size.toLong()).toInt()
                                src.readFully(buf, 0, n)
                                put(n)
                                left -= n
                            }
                        }
                        'D'.code -> {
                            var left = body.readLong()
                            if (left < 0) return false
                            while (left > 0) {
                                val n = minOf(left, buf.size.toLong()).toInt()
                                body.readFully(buf, 0, n)
                                put(n)
                                left -= n
                            }
                        }
                        'E'.code -> break@loop
                        else -> return false
                    }
                }
            }
        }
        return written == newSize && digest.digest().contentEquals(newSha)
    }

    fun sha256(f: File): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        f.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest()
    }

    private fun InputStream.readNBytesCompat(b: ByteArray): Int {
        var got = 0
        while (got < b.size) {
            val n = read(b, got, b.size - got)
            if (n < 0) break
            got += n
        }
        return got
    }
}
