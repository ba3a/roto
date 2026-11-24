package com.roto.app

import com.roto.core.preset.BuiltInPresets
import com.roto.core.preset.PresetLibrary
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WizardModelTest {

    private lateinit var root: Path
    private lateinit var model: WizardModel

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("roto-wizard")
        val builtIn = BuiltInPresets.ids().associateWith(BuiltInPresets::load)
        model = WizardModel(PresetLibrary(builtIn, root.resolve("presets")))
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun toNameStep() {
        model.choosePreset(model.presets.first())
        model.next()
    }

    @Test
    fun nextWaitsForAPreset() {
        assertFalse(model.canNext)
        model.next()
        assertEquals(WizardStep.Preset, model.state.step)
        assertNotNull(model.state.message)
        model.choosePreset(model.presets.first())
        assertTrue(model.canNext)
        assertNull(model.state.message)
        model.next()
        assertEquals(WizardStep.Name, model.state.step)
    }

    @Test
    fun idDefaultsToTheFolderNameMadeSafe() {
        toNameStep()
        model.chooseFolder(root.resolve("My Game!").createDirectories())
        assertEquals("My-Game", model.state.id)
        assertTrue(model.canNext)
    }

    @Test
    fun editedIdSurvivesAFolderChange() {
        toNameStep()
        model.chooseFolder(root.resolve("one").createDirectories())
        model.editId("mine")
        model.chooseFolder(root.resolve("two").createDirectories())
        assertEquals("mine", model.state.id)
    }

    @Test
    fun existingProjectBlocksWithOpenInstead() {
        toNameStep()
        val folder = root.resolve("game").createDirectories()
        folder.resolve("roto.json").writeText("{}")
        model.chooseFolder(folder)
        assertFalse(model.canNext)
        assertContains(model.state.message.orEmpty(), "Open it instead")
        assertEquals(folder, model.state.existingProject)
    }

    @Test
    fun invalidIdAndUnusableFolderBlock() {
        toNameStep()
        assertFalse(model.canNext)
        model.chooseFolder(root.resolve("game").createDirectories())
        model.editId("bad id")
        assertFalse(model.canNext)
        model.editId("good")
        assertTrue(model.canNext)
        val file = root.resolve("file.txt").also { it.writeText("x") }
        model.chooseFolder(file)
        assertFalse(model.canNext)
    }

    @Test
    fun backReturnsToThePreset() {
        toNameStep()
        model.back()
        assertEquals(WizardStep.Preset, model.state.step)
        assertEquals(model.presets.first().id, model.state.preset?.id)
    }
}
