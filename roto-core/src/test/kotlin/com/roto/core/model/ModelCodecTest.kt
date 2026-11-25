package com.roto.core.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelCodecTest {

    private val manifest =
        ModelManifest("walk", "east", loop = true, durations = listOf(80, 120), importedFrom = "walk.ase")

    @Test
    fun `reading what was written gives back an equal manifest`() {
        assertEquals(manifest, read(encode(manifest)))
    }

    @Test
    fun `unknown keys, missing fields and invalid values are refused`() {
        val valid = """{ "action": "walk", "facing": "east", "loop": true, "durations": [80], "importedFrom": "a" }"""
        assertEquals(1, read(valid).durations.size)
        listOf(
            valid.replace("}", ", \"extra\": 1 }"),
            valid.replace("\"loop\": true, ", ""),
            valid.replace("[80]", "[]"),
            valid.replace("[80]", "[0]"),
            valid.replace("walk", "wa lk"),
        ).forEach { text -> assertFailsWith<ModelException>(text) { read(text) } }
    }

    @Test
    fun `writing to a path round-trips and leaves no temporary file`() {
        val dir = createTempDirectory("roto-model")
        try {
            val file = dir.resolve("sub/model.json").also { ModelCodec.write(manifest, it) }

            assertEquals(manifest, ModelCodec.read(file))
            assertEquals(listOf("model.json"), file.parent.listDirectoryEntries().map { it.fileName.toString() })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun read(text: String): ModelManifest =
        ModelCodec.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun encode(value: ModelManifest): String =
        ByteArrayOutputStream().also { ModelCodec.write(value, it) }.toString(Charsets.UTF_8)
}
