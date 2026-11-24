package com.roto.core.preset

import com.roto.core.project.Canvas
import com.roto.core.project.ProjectConfig
import com.roto.core.template.PathTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OutputLayoutTest {

    private val folders = listOf("raw-art", "assets/sprites/hero")

    @Test
    fun `each layout yields templates a project config accepts`() {
        for (layout in OutputLayout.entries) {
            for (folder in folders) {
                val templates = layout.templates(folder)
                ProjectConfig(
                    id = "game",
                    palette = "palette.hex",
                    ramps = "ramps.json",
                    facings = listOf("EAST"),
                    actions = listOf("walk"),
                    canvas = Canvas(32, 32, 30),
                    output = templates.output,
                    manifest = templates.manifest,
                )
            }
        }
    }

    @Test
    fun `recognise round-trips templates for every layout`() {
        for (layout in OutputLayout.entries) {
            for (folder in folders) {
                val templates = layout.templates(folder)
                assertEquals(
                    RecognisedLayout(layout, folder),
                    OutputLayout.recognise(templates.output, templates.manifest),
                )
            }
        }
    }

    @Test
    fun `custom templates are not recognised`() {
        val custom = PathTemplate("art/{profile}_{action}_{facing}_{frame:000}.png")
        val manifest = PathTemplate("art/{profile}_{action}_{facing}.json")
        assertNull(OutputLayout.recognise(custom, manifest))
    }

    @Test
    fun `a pair from two layouts is not recognised`() {
        val perClip = OutputLayout.ONE_FOLDER_PER_CLIP.templates("art")
        val perProfile = OutputLayout.ONE_FOLDER_PER_PROFILE.templates("art")
        assertNull(OutputLayout.recognise(perClip.output, perProfile.manifest))
    }

    @Test
    fun `a pair with different folders is not recognised`() {
        val first = OutputLayout.ONE_FOLDER_PER_CLIP.templates("art")
        val second = OutputLayout.ONE_FOLDER_PER_CLIP.templates("other")
        assertNull(OutputLayout.recognise(first.output, second.manifest))
    }

    @Test
    fun `a folder that climbs out or holds a backslash is rejected`() {
        for (layout in OutputLayout.entries) {
            for (folder in listOf("..", "a/../b", "a\\b", "/abs", "art/", "")) {
                assertFailsWith<IllegalArgumentException>(folder) { layout.templates(folder) }
            }
        }
    }
}
