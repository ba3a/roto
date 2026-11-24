package com.roto.app

import com.roto.core.preset.BuiltInPresets
import com.roto.core.preset.PresetLibrary
import com.roto.core.project.ProjectConfigCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
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

    private fun toColoursStep() {
        toAnimationStep()
        model.next()
        model.next()
        assertEquals(WizardStep.Colours, model.state.step)
    }

    @Test
    fun presetFillsColoursAndOutput() {
        toColoursStep()
        assertNotNull(model.state.palette)
        assertTrue(model.state.ramps.isNotEmpty())
        assertTrue(model.canNext)
        model.state.ramps.map { it.name }.forEach(model::removeRamp)
        assertFalse(model.canNext)
        assertContains(model.state.message.orEmpty(), "at least one ramp")
        model.suggestRamps()
        assertTrue(model.canNext)
    }

    @Test
    fun badPaletteFileKeepsThePalette() {
        toColoursStep()
        val before = model.state.palette
        val file = root.resolve("bad.hex").also { it.writeText("ff0000\nnot-a-colour\n") }
        model.importPalette(file)
        assertEquals(before, model.state.palette)
        assertContains(model.state.message.orEmpty(), "line 2")
    }

    @Test
    fun importedPaletteDropsRampsAndOutlineItLacks() {
        toColoursStep()
        val file = root.resolve("two.hex").also { it.writeText("000000\nffffff\n") }
        model.importPalette(file)
        assertEquals(2, model.state.palette?.colors?.size)
        assertTrue(model.state.ramps.isEmpty())
        model.addRamp("bw", listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), model.state.ramps.single().colors)
        model.chooseOutline(0xFF123456.toInt())
        assertContains(model.state.message.orEmpty(), "not in the palette")
    }

    @Test
    fun importedPaletteDropsTheOutlineItLacks() {
        toColoursStep()
        model.importPalette(root.resolve("two.hex").also { it.writeText("000000\nffffff\n") })
        model.chooseOutline(0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), model.state.outline)
        model.importPalette(root.resolve("two.hex").also { it.writeText("ffffff\n808080\n") })
        assertNull(model.state.outline)
        assertContains(model.state.message.orEmpty(), "Dropped")
    }

    @Test
    fun rampErrorsNameTheColours() {
        toColoursStep()
        val file = root.resolve("two.hex").also { it.writeText("102030\n102030\n") }
        model.importPalette(file)
        model.addRamp("same", listOf(0xFF102030.toInt(), 0xFF102030.toInt()))
        assertTrue(model.state.ramps.isEmpty())
        assertContains(model.state.message.orEmpty(), "#102030 and #102030")
        model.addRamp("", listOf(0xFF102030.toInt()))
        assertContains(model.state.message.orEmpty(), "needs a name")
        model.addRamp("one", listOf(0xFF102030.toInt()))
        model.addRamp("one", listOf(0xFF102030.toInt()))
        assertContains(model.state.message.orEmpty(), "already exists")
    }

    private fun toOutputStep() {
        toColoursStep()
        model.next()
        assertEquals(WizardStep.Output, model.state.step)
    }

    @Test
    fun layoutRebuildsBothTemplatesFromTheFolder() {
        toOutputStep()
        model.editOutputFolder("art/out")
        model.chooseLayout(com.roto.core.preset.OutputLayout.ONE_FOLDER_PER_PROFILE)
        assertEquals("art/out/{profile}/{action}_{facing}_{frame}.png", model.state.output)
        assertEquals("art/out/{profile}/{action}_{facing}.json", model.state.manifest)
        assertTrue(model.canNext)
    }

    @Test
    fun outputFolderPickedInsideTheProjectIsKeptRelative() {
        toOutputStep()
        val game = model.state.folder!!
        model.chooseOutputFolder(game.resolve("art").resolve("out"))
        assertEquals("art/out", model.state.outputFolder)
        model.chooseOutputFolder(root.resolve("elsewhere"))
        assertContains(model.state.message.orEmpty(), "inside the project folder")
        assertEquals("art/out", model.state.outputFolder)
    }

    @Test
    fun advancedTemplateMissingAVariableShowsTheConfigMessage() {
        toOutputStep()
        model.editOutputTemplate("out/{profile}/{frame}.png")
        assertFalse(model.canNext)
        assertContains(model.state.message.orEmpty(), "must use every one of")
        model.chooseLayout(com.roto.core.preset.OutputLayout.ONE_FOLDER_PER_CLIP)
        assertTrue(model.canNext)
        model.editAfterPublish("tool {action}")
        assertContains(model.state.message.orEmpty(), "may only use {profile}")
        model.editAfterPublish("tool {profile}")
        assertTrue(model.canNext)
        model.editOutputFolder("../up")
        assertFalse(model.canNext)
    }

    private fun chooseGameFolder(): Path = root.resolve("game").also {
        model.chooseFolder(it.createDirectories())
    }

    private fun reviewReady(): Path {
        model.choosePreset(model.presets.first())
        model.next()
        val folder = chooseGameFolder()
        repeat(3) { model.next() }
        assertEquals(WizardStep.Colours, model.state.step)
        model.importPalette(root.resolve("p.hex").also { it.writeText("102030\n405060\n") })
        model.suggestRamps()
        model.next()
        model.editAfterPublish("tool {profile}")
        model.next()
        assertEquals(WizardStep.Review, model.state.step)
        return folder
    }

    @Test
    fun createWritesTheThreeFilesAndReturnsTheProject() {
        val folder = reviewReady()
        val reviewed = model.files
        val project = runBlocking { model.create() }
        assertNotNull(project, model.state.message)
        assertEquals(folder.toAbsolutePath().normalize(), project.root)
        assertEquals(listOf("tool", "{profile}"), project.config.afterPublish.map { it.source })
        assertEquals(3, reviewed.size)
        assertEquals(listOf("roto/palette.hex", "roto/ramps.json", "roto.json").map(folder::resolve), reviewed)
        reviewed.forEach { assertTrue(Files.exists(it)) }
        val written = ProjectConfigCodec.read(folder.resolve("roto.json"))
        assertEquals(listOf("tool", "{profile}"), written.afterPublish.map { it.source })
    }

    @Test
    fun aFailedCreateStaysOnReviewWithTheMessage() {
        val folder = reviewReady()
        folder.resolve("roto").createDirectories().resolve("palette.hex").writeText("000000\n")
        assertNull(runBlocking { model.create() })
        assertEquals(WizardStep.Review, model.state.step)
        assertContains(model.state.message.orEmpty(), "already exists")
        assertFalse(Files.exists(folder.resolve("roto.json")))
    }
}
