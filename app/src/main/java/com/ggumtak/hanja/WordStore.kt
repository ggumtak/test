package com.ggumtak.hanja

import android.content.Context
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * assets/words.tsv (tools/build_word_data.py): JMdict's written forms and their common readings. The asset is
 * stored uncompressed and memory-mapped, so opening it costs nothing and a lookup touches only a few pages.
 */
object WordStore {
    @Volatile
    private var tsv: SortedTsv? = null

    /** Blocking (cheap: a memory map): call from a background thread. */
    fun load(ctx: Context) {
        if (tsv != null) return
        synchronized(this) {
            if (tsv == null) tsv = SortedTsv(mapAsset(ctx, "words.tsv"))
        }
    }

    fun readings(word: String): List<String> =
        tsv?.find(word)?.split('・')?.filter { it.isNotEmpty() }.orEmpty()
}

/** The asset memory-mapped when it is stored uncompressed (build.gradle.kts: noCompress "tsv"), else read. */
internal fun mapAsset(ctx: Context, name: String): ByteBuffer =
    try {
        ctx.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
        }
    } catch (_: Exception) {
        ByteBuffer.wrap(ctx.assets.open(name).use { it.readBytes() })
    }

/**
 * "key\tvalue\n" lines sorted by the key's UTF-8 bytes, binary-searched by byte position: no index is built, so
 * a lookup reads only the lines it lands on. Reads are absolute, so one instance serves any thread.
 */
class SortedTsv(private val buf: ByteBuffer) {
    constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes))

    private val size = buf.limit()

    private fun lineStart(p: Int): Int {
        var i = p
        while (i > 0 && buf.get(i - 1) != NL) i--
        return i
    }

    private fun lineEnd(p: Int): Int {
        var i = p
        while (i < size && buf.get(i) != NL) i++
        return i
    }

    /** The value after [key]'s tab, or null when no line has that key. */
    fun find(key: String): String? {
        if (key.isEmpty() || size == 0) return null
        val k = key.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val s = lineStart(mid)
            val e = lineEnd(mid)
            val c = compare(k, s, e)
            when {
                c == 0 -> {
                    val from = s + k.size + 1
                    val out = ByteArray(maxOf(0, e - from))
                    for (i in out.indices) out[i] = buf.get(from + i)
                    return String(out, Charsets.UTF_8)
                }
                c < 0 -> hi = s
                else -> lo = e + 1
            }
        }
        return null
    }

    /** [key] against the key of the line [start, end) (up to its tab), as unsigned bytes. */
    private fun compare(key: ByteArray, start: Int, end: Int): Int {
        var i = 0
        while (true) {
            val p = start + i
            val keyEnded = p >= end || buf.get(p) == TAB
            if (i == key.size) return if (keyEnded) 0 else -1
            if (keyEnded) return 1
            val d = (key[i].toInt() and 0xFF) - (buf.get(p).toInt() and 0xFF)
            if (d != 0) return d
            i++
        }
    }

    private companion object {
        const val NL = '\n'.code.toByte()
        const val TAB = '\t'.code.toByte()
    }
}
