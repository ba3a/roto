package com.roto.core.ase

/**
 * A cursor over an in-memory byte array with the little-endian field types of the Aseprite format.
 * Reading past [limit] throws [AseFormatException] rather than an index error, so a truncated file
 * reports as a bad file.
 */
internal class LittleEndianInput(
    private val bytes: ByteArray,
    start: Int = 0,
    val limit: Int = bytes.size,
) {
    var position: Int = start
        private set

    val remaining: Int get() = limit - position

    fun byte(): Int {
        need(1)
        return bytes[position++].toInt() and BYTE_MAX
    }

    fun word(): Int = byte() or (byte() shl BITS_PER_BYTE)

    fun short(): Int = word().toShort().toInt()

    /** A DWORD as a non-negative `Int`; values above `Int.MAX_VALUE` are not a file Roto can hold. */
    fun dword(): Int {
        val value = word().toLong() or (word().toLong() shl BITS_PER_WORD)
        if (value > Int.MAX_VALUE) throw AseFormatException("DWORD $value is too large")
        return value.toInt()
    }

    fun string(): String {
        val length = word()
        return String(bytes(length), Charsets.UTF_8)
    }

    fun bytes(count: Int): ByteArray {
        need(count)
        val slice = bytes.copyOfRange(position, position + count)
        position += count
        return slice
    }

    fun skip(count: Int) {
        need(count)
        position += count
    }

    /** A cursor over the next [count] bytes; this cursor moves past them. */
    fun sub(count: Int): LittleEndianInput {
        need(count)
        val sub = LittleEndianInput(bytes, position, position + count)
        position += count
        return sub
    }

    private fun need(count: Int) {
        if (count < 0 || count > remaining) {
            throw AseFormatException("Unexpected end of data at byte $position: needed $count, had $remaining")
        }
    }

    private companion object {
        const val BYTE_MAX = 0xFF
        const val BITS_PER_BYTE = 8
        const val BITS_PER_WORD = 16
    }
}
