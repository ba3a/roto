package com.roto.core.ase

import java.io.ByteArrayOutputStream

/** An in-memory byte sink with the little-endian field types of the Aseprite format. */
internal class LittleEndianOutput {
    private val bytes = ByteArrayOutputStream()

    val size: Int get() = bytes.size()

    fun byte(value: Int) {
        require(value in 0..BYTE_MAX) { "BYTE out of range: $value" }
        bytes.write(value)
    }

    fun word(value: Int) {
        require(value in 0..WORD_MAX) { "WORD out of range: $value" }
        bytes.write(value and BYTE_MAX)
        bytes.write(value ushr BITS_PER_BYTE)
    }

    fun short(value: Int) {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "SHORT out of range: $value" }
        word(value and WORD_MAX)
    }

    fun dword(value: Int) {
        require(value >= 0) { "DWORD out of range: $value" }
        word(value and WORD_MAX)
        word(value ushr BITS_PER_WORD)
    }

    fun zeros(count: Int) {
        repeat(count) { bytes.write(0) }
    }

    /** STRING: a WORD byte length followed by the UTF-8 bytes, no terminator. */
    fun string(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        word(encoded.size)
        bytes.write(encoded)
    }

    fun bytes(value: ByteArray) {
        bytes.write(value)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    private companion object {
        const val BYTE_MAX = 0xFF
        const val WORD_MAX = 0xFFFF
        const val BITS_PER_BYTE = 8
        const val BITS_PER_WORD = 16
    }
}
