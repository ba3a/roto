package com.roto.core.json

import com.roto.core.io.AtomicFile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes one JSON file format; each format's codec is an `object` extending this.
 *
 * Reading is strict: an unknown key (most often a typo), a missing field, or a value that fails the
 * model's validation is an [IOException] from [error] with the reason, never a silent default. A
 * leading byte-order mark, which some Windows editors add, is accepted. Writing is pretty-printed
 * UTF-8 with `\n` line endings, so the same value always encodes to the same bytes, and a write to a
 * [Path] goes through [AtomicFile].
 *
 * [fileName] is the name files of this format go by, for example `roto.json`; error messages use it too.
 */
abstract class JsonCodec<T>(
    private val serializer: KSerializer<T>,
    val fileName: String,
    private val error: (message: String, cause: Throwable) -> IOException,
) {
    fun read(path: Path): T = decode(Files.readString(path), "$path: ")

    fun read(input: InputStream): T = decode(input.readAllBytes().toString(Charsets.UTF_8), where = "")

    fun write(value: T, path: Path) {
        AtomicFile.write(path) { write(value, it) }
    }

    fun write(value: T, output: OutputStream) {
        output.write((JSON.encodeToString(serializer, value) + "\n").toByteArray(Charsets.UTF_8))
    }

    private fun decode(text: String, where: String): T =
        try {
            JSON.decodeFromString(serializer, text.removePrefix(BYTE_ORDER_MARK))
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException too, so this covers both malformed
            // JSON and well-formed JSON whose values fail the model's validation.
            throw error("${where}Invalid $fileName: ${e.message}", e)
        }

    private companion object {
        const val BYTE_ORDER_MARK = "\uFEFF"

        val JSON = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
