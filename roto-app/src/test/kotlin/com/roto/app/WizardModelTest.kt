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

    private fun toAnimationStep() {
        toNameStep()
        model.chooseFolder(root.resolve("game").createDirectories())
        model.next()
    }

    @Test
    fun presetFillsTheGroups() {
        val preset = model.presets.first().preset
        model.choosePreset(model.presets.first())
        assertEquals(preset.facings, model.state.facings)
        assertEquals(preset.actions, model.state.actions)
        assertEquals(preset.canvas.width, model.state.width)
    }

    @Test
    fun badNamesDuplicatesAndEmptyListsBlock() {
        toAnimationStep()
        assertTrue(model.canNext)
        model.addFacing("bad name")
        assertFalse(model.canNext)
        model.removeFacing("bad name")
        model.addAction(model.state.actions.first().uppercase())
        assertFalse(model.canNext)
        assertContains(model.state.message.orEmpty(), "different")
        model.state.actions.forEach(model::removeAction)
        assertFalse(model.canNext)
        assertContains(model.state.message.orEmpty(), "at least one")
    }

    @Test
    fun renamingAFacingUpdatesItsPair() {
        toAnimationStep()
        model.state.mirrorPairs.toList().forEach { model.unpairFacings(it.first, it.second) }
        model.addFacing("EAST")
        model.addFacing("WEST")
        model.pairFacings("EAST", "WEST")
        model.renameFacing("EAST", "RIGHTWARD")
        assertContains(model.state.mirrorPairs.map { it.toString() }, "[RIGHTWARD, WEST]")
        model.removeFacing("WEST")
        assertTrue(model.state.mirrorPairs.none { "WEST" in it.toString() })
    }

    @Test
    fun aPairedFacingCannotBePairedAgain() {
        toAnimationStep()
        model.state.mirrorPairs.toList().forEach { model.unpairFacings(it.first, it.second) }
        listOf("A", "B", "C").forEach(model::addFacing)
        model.pairFacings("A", "B")
        model.pairFacings("B", "C")
        assertEquals(1, model.state.mirrorPairs.size)
        assertContains(model.state.message.orEmpty(), "only one")
    }

    @Test
    fun badCanvasBlocks() {
        toAnimationStep()
        model.next()
        assertEquals(WizardStep.Canvas, model.state.step)
        assertTrue(model.canNext)
        model.editGroundRow(model.state.height)
        assertFalse(model.canNext)
        assertNotNull(model.state.message)
        model.editWidth(0)
        assertFalse(model.canNext)
    }

    @Test
    fun presetSwitchKeepsEditedGroupsOnly() {
        model.choosePreset(model.presets.first())
        model.addAction("extra")
        val other = model.presets.last()
        model.choosePreset(other)
        assertTrue("extra" in model.state.actions)
        assertEquals(other.preset.facings, model.state.facings)
        assertEquals(other.preset.canvas.width, model.state.width)
    }
}
