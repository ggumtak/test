package com.ggumtak.hanja

import android.content.Context
import java.nio.ByteBuffer
import java.util.zip.Inflater

/** A dictionary the stores look keys up in: the value after [key]'s tab, or null when no line has that key. */
interface Table {
    fun find(key: String): String?
}

/**
 * assets/NAME.pk when the build packed the dictionary (buildSrc/PackTsv.java, all of them in a release), else
 * assets/NAME.tsv as it is. Blocking: call from a background thread.
 */
internal fun openTable(ctx: Context, name: String, keep: Int = PackedTsv.KEEP_ALL): Table =
    runCatching { PackedTsv(mapAsset(ctx, "$name.pk"), keep) as Table }.getOrElse { SortedTsv(mapAsset(ctx, "$name.tsv")) }

/**
 * A sorted TSV packed by buildSrc/PackTsv.java: its lines in blocks of about 16 KB, each deflated, behind an index
 * of every block's first key. The index (a few hundred keys) is read once; a lookup finds the block there, inflates
 * that one block and searches it as a [SortedTsv]. The blocks used last stay inflated ([keep] of them: all of a
 * small dictionary, a few of the two big ones), so a card's rows find what the background thread already read for
 * them. The asset is memory-mapped and stored uncompressed in the APK (build.gradle.kts: noCompress "pk"). Any
 * thread may call [find].
 */
class PackedTsv(private val buf: ByteBuffer, private val keep: Int = KEEP_ALL) : Table {
    constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes))

    private val keys: Array<ByteArray>
    /** Where each block starts in [buf], and the end of the last one. */
    private val offsets: IntArray
    private val rawSizes: IntArray
    /** Block number → the block inflated, the least recently used first. */
    private val blocks = object : LinkedHashMap<Int, SortedTsv>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SortedTsv>): Boolean = size > keep
    }

    init {
        val b = buf.duplicate()
        b.position(0)
        require(b.remaining() >= 12 && b.get() == 'H'.code.toByte() && b.get() == 'J'.code.toByte() &&
            b.get() == 'P'.code.toByte() && b.get() == '1'.code.toByte()) { "not a packed dictionary" }
        val count = b.int
        val indexBytes = b.int
        require(count >= 0 && indexBytes >= 0 && 12L + indexBytes <= buf.limit()) { "bad index" }
        keys = Array(count) { EMPTY }
        offsets = IntArray(count + 1)
        rawSizes = IntArray(count)
        var at = 12 + indexBytes
        for (i in 0 until count) {
            val key = ByteArray(b.short.toInt() and 0xFFFF)
            b.get(key)
            keys[i] = key
            offsets[i] = at
            at += b.int
            rawSizes[i] = b.int
        }
        offsets[count] = at
        require(at <= buf.limit()) { "the blocks run past the end" }
    }

    override fun find(key: String): String? {
        if (key.isEmpty() || keys.isEmpty()) return null
        val k = key.toByteArray(Charsets.UTF_8)
        // The last block whose first key is not after [key].
        var lo = 0
        var hi = keys.size - 1
        var at = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (compare(keys[mid], k) <= 0) {
                at = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (at < 0) return null
        return block(at).find(key)
    }

    private fun block(i: Int): SortedTsv = synchronized(blocks) {
        blocks[i] ?: SortedTsv(inflate(i)).also { blocks[i] = it }
    }

    private fun inflate(i: Int): ByteArray {
        val length = offsets[i + 1] - offsets[i]
        // One spare byte: a raw deflate stream may need a byte after its end to finish.
        val packed = ByteArray(length + 1)
        val b = buf.duplicate()
        b.position(offsets[i])
        b.get(packed, 0, length)
        val out = ByteArray(rawSizes[i])
        val inflater = Inflater(true)
        try {
            inflater.setInput(packed)
            var n = 0
            while (n < out.size) {
                val got = inflater.inflate(out, n, out.size - n)
                if (got == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                n += got
            }
            check(n == out.size) { "block $i is damaged" }
        } finally {
            inflater.end()
        }
        return out
    }

    companion object {
        /** Every block stays once read: for the dictionaries of a megabyte or less. */
        const val KEEP_ALL = 128
        private val EMPTY = ByteArray(0)

        /** As unsigned bytes, a key another begins with first: the order of the lines (SortedTsv, PackTsv). */
        private fun compare(a: ByteArray, b: ByteArray): Int {
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                if (d != 0) return d
            }
            return a.size - b.size
        }
    }
}
