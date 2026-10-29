package com.roto.core.project

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes [ProjectConfig] as `roto.json`.
 *
 * Reading is strict: an unknown key (most often a typo), a missing field, or a value that fails
 * [ProjectConfig]'s validation is a [ProjectConfigException] with the reason, never a silent default.
 * A leading byte-order mark, which some Windows editors add, is accepted. Writing is UTF-8 with `\n`
 * line endings, so the same config always encodes to the same bytes.
 */
object ProjectConfigCodec {

    /** The file name a consuming project's config goes by, at the project root. */
    const val FILE_NAME = "roto.json"

    private const val BYTE_ORDER_MARK = '﻿'

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun read(path: Path): ProjectConfig =
        try {
            decode(Files.readString(path))
        } catch (e: ProjectConfigException) {
            throw ProjectConfigException("$path: ${e.message}", e)
        }

    fun read(input: InputStream): ProjectConfig = decode(input.readAllBytes().toString(Charsets.UTF_8))

    fun write(config: ProjectConfig, path: Path) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { write(config, it) }
    }

    fun write(config: ProjectConfig, output: OutputStream) {
        output.write(encode(config).toByteArray(Charsets.UTF_8))
    }

    private fun encode(config: ProjectConfig): String = json.encodeToString(ProjectConfig.serializer(), config) + "\n"

    private fun decode(text: String): ProjectConfig =
        try {
            json.decodeFromString(ProjectConfig.serializer(), text.removePrefix(BYTE_ORDER_MARK.toString()))
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException too, so this covers both malformed
            // JSON and a well-formed config that fails validation.
            throw ProjectConfigException("Invalid ${FILE_NAME}: ${e.message}", e)
        }
}
