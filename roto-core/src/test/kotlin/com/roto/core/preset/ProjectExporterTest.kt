package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.template.PathTemplate
import com.roto.core.template.Template
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectExporterTest {
    private val dark = 0xFF202020.toInt()
    private val mid = 0xFF808080.toInt()
    private val palette = Palette(listOf(dark, mid))
    private val ramps = RampSet(ramps = listOf(Ramp("skin", listOf(dark, mid))), outline = dark)

    private fun preset(layout: OutputLayout, folder: String) = ProjectPreset(
        "Side", "A side view", listOf("LEFT", "RIGHT"), listOf(MirrorPair("LEFT", "RIGHT")), listOf("idle", "walk"),
        Canvas(32, 32, 30), layout, folder, palette, ramps,
    )

    @Test
    fun `export of a scaffolded preset gives the preset back`() {
        for (layout in OutputLayout.entries) {
            val preset = preset(layout, "art/frames")
            val project = ProjectScaffolder.scaffold(preset, "game", Files.createTempDirectory("export"))

            assertEquals(preset, ProjectExporter.export(project, preset.name, preset.description))
        }
    }

    @Test
    fun `custom templates are refused`() {
        val project = ProjectScaffolder.scaffold(
            preset(OutputLayout.ONE_FOLDER_PER_CLIP, "art"), "game", Files.createTempDirectory("export"),
        )
        val custom = project.config.copy(output = PathTemplate("x/{profile}_{action}_{facing}_{frame}.png"))

        val e = assertFailsWith<PresetException> { ProjectExporter.export(Project(project.root, custom), "n", "d") }

        assertEquals("These output paths are custom; choose a layout to export", e.message)
    }

    @Test
    fun `afterPublish does not reach the preset`() {
        val root = Files.createTempDirectory("export")
        val project = ProjectScaffolder.scaffold(preset(OutputLayout.ONE_FOLDER_PER_PROFILE, "art"), "game", root)
        val withHook = project.config.copy(afterPublish = listOf(Template("run"), Template("{profile}")))
        ProjectConfigCodec.write(withHook, root.resolve(ProjectConfigCodec.fileName))

        val exported = ProjectExporter.export(Project.open(root), "Side", "A side view")

        assertEquals(preset(OutputLayout.ONE_FOLDER_PER_PROFILE, "art"), exported)
        assertEquals(emptyList(), exported.toConfig("game").afterPublish)
    }
}
