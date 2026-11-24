package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.ProjectPalette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectScaffolderTest {
    private val dark = 0xFF202020.toInt()
    private val mid = 0xFF808080.toInt()
    private val preset = ProjectPreset(
        "Side", "A side view", listOf("LEFT", "RIGHT"), listOf(MirrorPair("LEFT", "RIGHT")), listOf("idle"),
        Canvas(32, 32, 30), OutputLayout.ONE_FOLDER_PER_CLIP, "art",
        Palette(listOf(dark, mid)), RampSet(ramps = listOf(Ramp("skin", listOf(dark, mid)))),
    )

    private fun folder(): Path = Files.createTempDirectory("scaffold")

    @Test
    fun `the result loads through Project open and ProjectPalette load`() {
        val root = folder()

        val project = ProjectScaffolder.scaffold(preset, "game", root)

        assertEquals("game", project.config.id)
        assertEquals(preset.palette, ProjectPalette.load(project).palette)
        assertEquals(preset.ramps, ProjectPalette.load(project).ramps)
    }

    @Test
    fun `an existing file stops the call with nothing written`() {
        for (existing in listOf("roto.json", "roto/palette.hex", "roto/ramps.json")) {
            val root = folder()
            root.resolve(existing).also { it.parent.createDirectories() }.writeText("x")

            assertFailsWith<ScaffoldException> { ProjectScaffolder.scaffold(preset, "game", root) }

            val files = Files.walk(root).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
            assertEquals(listOf(root.resolve(existing)), files)
        }
    }

    @Test
    fun `a failing write leaves no roto json and lists the files written`() {
        for (failAt in 1..3) {
            val root = folder()
            var calls = 0
            val writer = ScaffoldWriter { _, write ->
                if (++calls == failAt) throw IOException("disk full")
                write()
            }

            val error = assertFailsWith<ScaffoldException> { ProjectScaffolder.scaffold(preset, "game", root, writer) }

            assertEquals(failAt - 1, error.written.size)
            error.written.forEach { assertTrue(Files.exists(it) && it.toString() in error.message.orEmpty()) }
            assertFalse(Files.exists(root.resolve("roto.json")))
        }
    }

    @Test
    fun `roto json is written last`() {
        val order = mutableListOf<String>()

        ProjectScaffolder.scaffold(preset, "game", folder()) { file, write ->
            order += file.fileName.toString()
            write()
        }

        assertEquals(listOf("palette.hex", "ramps.json", "roto.json"), order)
    }

    @Test
    fun `an unsafe id is refused before anything is written`() {
        val root = folder()

        assertFailsWith<IllegalArgumentException> { ProjectScaffolder.scaffold(preset, "../x", root) }

        assertFalse(Files.exists(root.resolve("roto.json")))
    }
}
