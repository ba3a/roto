package com.roto.core.preset

import com.roto.core.palette.ProjectPalette
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltInPresetsTest {
    private val expected = listOf("blank", "side-view", "top-down-4", "top-down-8")

    @Test
    fun `every listed preset loads`() {
        assertEquals(expected, BuiltInPresets.ids())
        assertEquals(expected.size, BuiltInPresets.all().size)
    }

    @Test
    fun `palettes hold 16 to 32 colours and ramps number three to five`() {
        for (preset in BuiltInPresets.all()) {
            assertTrue(preset.palette.colors.size in 16..32, "${preset.name}: ${preset.palette.colors.size} colours")
            assertTrue(preset.ramps.ramps.size in 3..5, "${preset.name}: ${preset.ramps.ramps.size} ramps")
        }
    }

    @Test
    fun `an unknown id is refused`() {
        assertFailsWith<PresetException> { BuiltInPresets.load("nope") }
    }

    @Test
    fun `scaffolding every preset loads through Project open and ProjectPalette load`() {
        for (id in BuiltInPresets.ids()) {
            val preset = BuiltInPresets.load(id)
            val folder = Files.createTempDirectory("builtin")

            val project = ProjectScaffolder.scaffold(preset, "demo", folder)

            val loaded = ProjectPalette.load(project)
            assertEquals(preset.palette, loaded.palette, id)
            assertEquals(preset.ramps, loaded.ramps, id)
        }
    }

    @Test
    fun `no preset names a game, an engine or a consumer layout`() {
        val banned = listOf("unity", "unreal", "godot", "gamemaker", "assets/", "resources/", "src/")
        for (preset in BuiltInPresets.all()) {
            val text = listOf(
                preset.name, preset.description, preset.outputFolder, preset.facings.joinToString(),
                preset.actions.joinToString(),
            ).joinToString(" ").lowercase()
            for (word in banned) assertFalse(word in text, "${preset.name} mentions '$word'")
        }
    }
}
