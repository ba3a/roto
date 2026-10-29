package com.roto.core.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProjectConfigCodecTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-project-config")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `reads the spec example`() {
        val config = javaClass.getResourceAsStream("/project/roto.json")!!.use { ProjectConfigCodec.read(it) }

        assertEquals(sampleConfig(), config)
    }

    @Test
    fun `reading what was written gives back an equal config`() {
        assertEquals(sampleConfig(), read(encode(sampleConfig())))
    }

    @Test
    fun `round trip through a file`() {
        val file = tempRoot.resolve("game").resolve(ProjectConfigCodec.FILE_NAME)

        ProjectConfigCodec.write(sampleConfig(), file)

        assertEquals(sampleConfig(), ProjectConfigCodec.read(file))
    }

    @Test
    fun `writing is byte-stable and keeps the documented field names and shapes`() {
        val text = encode(sampleConfig())

        assertEquals(text, encode(read(text)))
        assertFalse('\r' in text)
        assertEquals('\n', text.last())
        listOf(
            "\"palette\"", "\"ramps\"", "\"facings\"", "\"mirrorPairs\"", "\"actions\"", "\"canvas\"",
            "\"groundRow\"", "\"output\"", "\"manifest\"", "\"afterPublish\"",
        ).forEach { assertContains(text, it) }
        assertContains(text, "\"raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png\"")
        // A mirror pair is a two-element array, not an object.
        assertContains(text.replace(Regex("\\s"), ""), "\"mirrorPairs\":[[\"EAST\",\"WEST\"]]")
    }

    @Test
    fun `mirrorPairs and afterPublish may be left out`() {
        val config = read(
            """
            {
              "palette": "p.gpl", "ramps": "r.json", "facings": ["SOUTH"], "actions": ["idle"],
              "canvas": { "width": 16, "height": 16, "groundRow": 15 },
              "output": "{profile}/{action}_{facing}/{frame}.png",
              "manifest": "{profile}/{action}_{facing}.json"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), config.mirrorPairs)
        assertEquals(emptyList(), config.afterPublish)
    }

    @Test
    fun `a leading byte order mark is accepted`() {
        assertEquals(sampleConfig(), read("﻿" + encode(sampleConfig())))
    }

    @Test
    fun `unknown keys are rejected rather than ignored`() {
        val text = encode(sampleConfig()).replace("\"palette\"", "\"pallete\"")

        val error = assertFailsWith<ProjectConfigException> { read(text) }
        assertContains(error.message.orEmpty(), "pallete")
    }

    @Test
    fun `missing fields, malformed JSON and invalid values are config errors`() {
        val valid = encode(sampleConfig())

        assertFailsWith<ProjectConfigException> { read(valid.replace(Regex("\"ramps\": \"[^\"]*\","), "")) }
        assertFailsWith<ProjectConfigException> { read(valid.dropLast(3)) }
        assertFailsWith<ProjectConfigException> { read(valid.replace("\"groundRow\": 59", "\"groundRow\": 60")) }
        assertFailsWith<ProjectConfigException> { read(valid.replace("{frame:000}", "{frame:lower}")) }
        assertFailsWith<ProjectConfigException> { read(valid.replace("\"WEST\"\n", "\"WEST\", \"NORTH\"\n")) }
    }

    @Test
    fun `a mirror pair must have exactly two facings`() {
        val pairs = Regex("\"mirrorPairs\": \\[\\s*\\[[^\\]]*\\]\\s*\\]")
        val text = encode(sampleConfig()).replace(pairs, "\"mirrorPairs\": [[\"EAST\"]]")
        check(text != encode(sampleConfig()))

        assertFailsWith<ProjectConfigException> { read(text) }
    }

    @Test
    fun `errors reading a file name the file`() {
        val file = tempRoot.resolve(ProjectConfigCodec.FILE_NAME)
        Files.writeString(file, "{}")

        val error = assertFailsWith<ProjectConfigException> { ProjectConfigCodec.read(file) }
        assertContains(error.message.orEmpty(), file.toString())
    }

    private fun read(text: String): ProjectConfig =
        ProjectConfigCodec.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun encode(config: ProjectConfig): String =
        ByteArrayOutputStream().also { ProjectConfigCodec.write(config, it) }.toString(Charsets.UTF_8)
}
