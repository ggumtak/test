package com.ggumtak.hanja

import java.io.ByteArrayOutputStream

/**
 * Minimal proto3 wire-format reader and selective string rewriter for Anki's SQLite blobs (Notetype.Config,
 * Notetype.Template.Config, PackageMetadata). Non-replaced fields are copied byte-for-byte so unknown or
 * Anki-internal fields (field/template ids, deck overrides, browser fonts, etc.) are preserved losslessly.
 */
object ProtoWire {
    private const val WIRE_VARINT = 0
    private const val WIRE_64BIT = 1
    private const val WIRE_LEN = 2
    private const val WIRE_32BIT = 5

    /** Last varint value for [fieldNumber], or null when absent. */
    fun getVarint(data: ByteArray, fieldNumber: Int): Long? {
        var found: Long? = null
        scan(data) { field, wire, valueStart, valueEnd ->
            if (field == fieldNumber && wire == WIRE_VARINT) {
                found = readVarint(data, valueStart).first
            }
        }
        return found
    }

    /** Last UTF-8 string value for [fieldNumber], or "" (proto3 default) when absent. */
    fun getString(data: ByteArray, fieldNumber: Int): String {
        var found = ""
        scan(data) { field, wire, valueStart, valueEnd ->
            if (field == fieldNumber && wire == WIRE_LEN) {
                found = String(data, valueStart, valueEnd - valueStart, Charsets.UTF_8)
            }
        }
        return found
    }

    /**
     * Rewrites the UTF-8 string fields in [replacements] (fieldNumber -> value) while copying every other field's
     * raw tag + payload bytes verbatim. Empty replacement strings are omitted per proto3 defaults.
     */
    fun replaceStrings(data: ByteArray, replacements: Map<Int, String>): ByteArray {
        val out = ByteArrayOutputStream(data.size + replacements.values.sumOf { it.length } + 64)
        scan(data) { field, _, _, fieldEnd, fieldStart ->
            if (field !in replacements) {
                out.write(data, fieldStart, fieldEnd - fieldStart)
            }
        }
        for ((field, value) in replacements.entries.sortedBy { it.key }) {
            if (value.isEmpty()) continue
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeVarint(out, (field.toLong() shl 3) or WIRE_LEN.toLong())
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }
        return out.toByteArray()
    }

    /** Encodes a single varint field (e.g. PackageMetadata { version = 3 } -> [0x08, 0x03]). */
    fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream(16)
        writeVarint(out, (fieldNumber.toLong() shl 3) or WIRE_VARINT.toLong())
        writeVarint(out, value)
        return out.toByteArray()
    }

    private inline fun scan(
        data: ByteArray,
        onField: (field: Int, wire: Int, valueStart: Int, valueEnd: Int) -> Unit,
    ) = scan(data) { field, wire, valueStart, valueEnd, _ -> onField(field, wire, valueStart, valueEnd) }

    private inline fun scan(
        data: ByteArray,
        onField: (field: Int, wire: Int, valueStart: Int, valueEnd: Int, fieldStart: Int) -> Unit,
    ) {
        var pos = 0
        while (pos < data.size) {
            val fieldStart = pos
            val (tag, afterTag) = readVarint(data, pos)
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7L).toInt()
            if (field <= 0) throw IllegalArgumentException("Invalid protobuf field number $field")
            when (wire) {
                WIRE_VARINT -> {
                    val (_, end) = readVarint(data, afterTag)
                    onField(field, wire, afterTag, end, fieldStart)
                    pos = end
                }
                WIRE_64BIT -> {
                    val end = afterTag + 8
                    if (end > data.size) throw IllegalArgumentException("Truncated 64-bit protobuf field")
                    onField(field, wire, afterTag, end, fieldStart)
                    pos = end
                }
                WIRE_LEN -> {
                    val (lenLong, valueStart) = readVarint(data, afterTag)
                    if (lenLong < 0 || lenLong > data.size - valueStart) {
                        throw IllegalArgumentException("Invalid protobuf length $lenLong")
                    }
                    val end = valueStart + lenLong.toInt()
                    onField(field, wire, valueStart, end, fieldStart)
                    pos = end
                }
                WIRE_32BIT -> {
                    val end = afterTag + 4
                    if (end > data.size) throw IllegalArgumentException("Truncated 32-bit protobuf field")
                    onField(field, wire, afterTag, end, fieldStart)
                    pos = end
                }
                else -> throw IllegalArgumentException("Unsupported protobuf wire type $wire")
            }
        }
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < data.size) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result to pos
            shift += 7
            if (shift >= 64) throw IllegalArgumentException("Malformed protobuf varint")
        }
        throw IllegalArgumentException("Truncated protobuf varint")
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
    }
}
