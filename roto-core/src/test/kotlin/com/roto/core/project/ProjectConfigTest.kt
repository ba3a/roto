package com.roto.core.project

import com.roto.core.template.PathTemplate
import com.roto.core.template.Template
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ProjectConfigTest {

    private val config = sampleConfig()

    @Test
    fun `the spec example is valid`() {
        sampleConfig()
    }

    @Test
    fun `palette and ramps must be relative paths inside the project`() {
        assertFailsWith<IllegalArgumentException> { config.copy(palette = "/palette/faultory.gpl") }
        assertFailsWith<IllegalArgumentException> { config.copy(palette = "../palette.gpl") }
        assertFailsWith<IllegalArgumentException> { config.copy(ramps = "palette\\ramps.json") }
        assertFailsWith<IllegalArgumentException> { config.copy(ramps = "") }
    }

    @Test
    fun `facings and actions must be non-empty lists of file-safe names`() {
        assertFailsWith<IllegalArgumentException> { config.copy(facings = emptyList(), mirrorPairs = emptyList()) }
        assertFailsWith<IllegalArgumentException> { config.copy(actions = emptyList()) }
        assertFailsWith<IllegalArgumentException> { config.copy(actions = listOf("walk", "walk/run")) }
        assertFailsWith<IllegalArgumentException> { config.copy(actions = listOf("walk", "")) }
        assertFailsWith<IllegalArgumentException> { config.copy(actions = listOf("_walk")) }
        assertFailsWith<IllegalArgumentException> { config.copy(actions = listOf("walk fast")) }
    }

    @Test
    fun `names that differ only in case are rejected because templates may change case`() {
        val error = assertFailsWith<IllegalArgumentException> {
            config.copy(facings = listOf("NORTH", "EAST", "east", "WEST"))
        }

        assertContains(error.message.orEmpty(), "EAST")
    }

    @Test
    fun `mirror pairs must name two distinct known facings, each in at most one pair`() {
        assertFailsWith<IllegalArgumentException> { config.copy(mirrorPairs = listOf(MirrorPair("EAST", "UP"))) }
        assertFailsWith<IllegalArgumentException> { MirrorPair("EAST", "EAST") }
        assertFailsWith<IllegalArgumentException> {
            config.copy(mirrorPairs = listOf(MirrorPair("EAST", "WEST"), MirrorPair("NORTH", "EAST")))
        }
    }

    @Test
    fun `canvas size and ground row are checked`() {
        assertFailsWith<IllegalArgumentException> { Canvas(width = 0, height = 60, groundRow = 59) }
        assertFailsWith<IllegalArgumentException> { Canvas(width = 40, height = 70_000, groundRow = 59) }
        assertFailsWith<IllegalArgumentException> { Canvas(width = 40, height = 60, groundRow = 60) }
        assertFailsWith<IllegalArgumentException> { Canvas(width = 40, height = 60, groundRow = -1) }
    }

    @Test
    fun `output must use every variable so no two frames share a file`() {
        listOf(
            "raw-art/{action}_{facing:lower}/{frame:000}.png",
            "raw-art/{profile}/{facing:lower}/{frame:000}.png",
            "raw-art/{profile}/{action}/{frame:000}.png",
            "raw-art/{profile}/{action}_{facing:lower}/frame.png",
        ).forEach { source ->
            assertFailsWith<IllegalArgumentException>(source) { config.copy(output = PathTemplate(source)) }
        }
    }

    @Test
    fun `manifest must be one per clip`() {
        assertFailsWith<IllegalArgumentException> {
            config.copy(manifest = PathTemplate("raw-art/{profile}/{action}_{facing:lower}/{frame}.json"))
        }
        assertFailsWith<IllegalArgumentException> {
            config.copy(manifest = PathTemplate("raw-art/{profile}/{action}/clip.json"))
        }
    }

    @Test
    fun `afterPublish arguments may only use the profile`() {
        assertFailsWith<IllegalArgumentException> {
            config.copy(afterPublish = listOf(Template("sync"), Template("--facing={facing}")))
        }
    }
}
