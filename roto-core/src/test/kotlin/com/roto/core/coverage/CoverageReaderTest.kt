package com.roto.core.coverage

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseCodec
import com.roto.core.handoff.HandoffFrame
import com.roto.core.handoff.HandoffMerge
import com.roto.core.handoff.HandoffRender
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.model.ModelClip
import com.roto.core.model.ModelFrame
import com.roto.core.model.ModelRepository
import com.roto.core.palette.Palette
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.TimeRange
import com.roto.core.project.Project
import com.roto.core.project.sampleConfig
import com.roto.core.publish.BaseProfile
import com.roto.core.publish.Publisher
import com.roto.core.publish.Retime
import com.roto.core.variant.RampSwapPair
import com.roto.core.variant.VariantOf
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageReaderTest {

    private lateinit var tempRoot: Path
    private lateinit var project: Project
    private lateinit var folder: ProfileFolder

    private val palette = Palette(listOf(Argb.of(0, 0, 0), Argb.of(200, 30, 30), Argb.of(255, 255, 255)))
    private val walkEast = Clip("walk", "EAST", "a.MOV", TimeRange(0, 1_000), mirror = true)
    private val idleSouth = Clip("idle", "SOUTH", "b.MOV", TimeRange(0, 1_000))
    private val workingNorth = Clip("working", "NORTH", "c.MOV", TimeRange(0, 1_000))
    private val carryNorth = Clip("carry", "NORTH", "d.MOV", TimeRange(0, 1_000))
    private val profile = Profile(clips = listOf(walkEast, idleSouth, workingNorth, carryNorth))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-coverage")
        project = Project(tempRoot.resolve("game"), sampleConfig().copy(afterPublish = emptyList()))
        folder = ProfileFolder(tempRoot.resolve("library").resolve("faultory").resolve("worker"))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a cell with nothing is not filmed`() {
        assertEquals(CoverageCell(CoverageState.NotFilmed), reader().cell("idle", "NORTH"))
    }

    @Test
    fun `the paired facing of a mirrored clip is mirrored, unless filmed`() {
        assertEquals(CoverageState.Mirrored, reader().cell("walk", "WEST").state)
        val filmed = profile.copy(clips = profile.clips + walkEast.copy(facing = "WEST", mirror = false))
        assertEquals(CoverageState.Setup, reader(filmed).cell("walk", "WEST").state)
    }

    @Test
    fun `an unfilmed cell with a model is a model`() {
        saveModel("idle", "NORTH")

        assertEquals(CoverageCell(CoverageState.Model, hasModel = true), reader().cell("idle", "NORTH"))
    }

    @Test
    fun `a clip with no handoff is in setup`() {
        assertEquals(CoverageState.Setup, reader().cell("idle", "SOUTH").state)
    }

    @Test
    fun `a clip with art on some frames is painting`() {
        handoff(idleSouth, listOf(100, 100, 100), painted = 2)

        assertEquals(CoverageState.Painting(2, 3), reader().cell("idle", "SOUTH").state)
    }

    @Test
    fun `a fully painted clip that was not published is finished`() {
        handoff(idleSouth, listOf(100))

        assertEquals(CoverageState.Finished, reader().cell("idle", "SOUTH").state)
    }

    @Test
    fun `published frames that match the art are published`() {
        handoff(walkEast, listOf(120, 80))
        publish(walkEast)

        assertEquals(CoverageState.Published, reader().cell("walk", "EAST").state)
    }

    @Test
    fun `a retime changed after publish is out of date, and republishing makes it published again`() {
        handoff(walkEast, listOf(120, 80))
        publish(walkEast)
        val faster = walkEast.copy(retime = Retime(speedPercent = 200))
        val changed = Profile(clips = listOf(faster, idleSouth, workingNorth, carryNorth))

        assertTrue(reader(changed).cell("walk", "EAST").state is CoverageState.OutOfDate)
        Publisher(project).publish("worker", changed, folder, listOf(faster))
        assertEquals(CoverageState.Published, reader(changed).cell("walk", "EAST").state)
    }

    @Test
    fun `a frame repainted after publish is out of date`() {
        handoff(walkEast, listOf(120, 80))
        publish(walkEast)
        handoff(walkEast, listOf(120, 80), x = 10)

        assertTrue(reader().cell("walk", "EAST").state is CoverageState.OutOfDate)
    }

    @Test
    fun `a missing frame file, a changed duration or a changed count is out of date`() {
        handoff(idleSouth, listOf(100, 100))
        publish(idleSouth)
        Files.delete(project.frameFile("worker", "idle", "SOUTH", 1))
        assertTrue(reader().cell("idle", "SOUTH").state is CoverageState.OutOfDate)

        publish(idleSouth)
        handoff(idleSouth, listOf(100, 90))
        assertTrue(reader().cell("idle", "SOUTH").state is CoverageState.OutOfDate)

        handoff(idleSouth, listOf(100))
        assertTrue(reader().cell("idle", "SOUTH").state is CoverageState.OutOfDate)
    }

    @Test
    fun `an unreadable manifest is out of date with the codec message`() {
        handoff(idleSouth, listOf(100))
        publish(idleSouth)
        project.manifestFile("worker", "idle", "SOUTH").writeText("not json")

        val state = reader().cell("idle", "SOUTH").state

        assertTrue(state is CoverageState.OutOfDate)
        assertTrue(state.reason.contains("clip manifest"), state.reason)
    }

    @Test
    fun `an unreadable handoff is unreadable with its reason`() {
        handoff(idleSouth, listOf(100))
        folder.handoffFile(idleSouth).writeText("not an ase")

        val state = reader().cell("idle", "SOUTH").state

        assertTrue(state is CoverageState.Unreadable)
        assertFalse(state.reason.isEmpty())
    }

    @Test
    fun `a filmed clip with a model keeps its state and marks the model`() {
        saveModel("idle", "SOUTH")

        assertEquals(CoverageCell(CoverageState.Setup, hasModel = true), reader().cell("idle", "SOUTH"))
    }

    @Test
    fun `read assembles every cell, and two runs agree`() {
        handoff(idleSouth, listOf(100, 100), painted = 1)
        handoff(walkEast, listOf(100))
        publish(walkEast)
        saveModel("carry", "WEST")

        val first = reader().read()
        val second = reader().read()

        assertEquals(listOf("idle", "walk", "working", "carry"), first.actions)
        assertEquals(CoverageState.Painting(1, 2), first.cell("idle", "SOUTH").state)
        assertEquals(CoverageState.Mirrored, first.cell("walk", "WEST").state)
        assertEquals(CoverageState.Model, first.cell("carry", "WEST").state)
        for (action in first.actions) {
            for (facing in first.facings) {
                assertEquals(first.cell(action, facing), second.cell(action, facing))
            }
        }
        assertEquals(first.next, second.next)
    }

    @Test
    fun `a variant shows the base's progress, published only against the swapped art`() {
        val variantProject = variantProject()
        val variant = Profile(variant = VariantOf("worker", listOf(RampSwapPair("skin", "dark"))))
        handoff(walkEast, listOf(120, 80))
        handoff(idleSouth, listOf(100, 100), painted = 1)
        val variantReader = { of: Profile -> variantReader(variantProject, of) }

        assertEquals(CoverageState.Finished, variantReader(variant).cell("walk", "EAST").state)
        assertEquals(CoverageState.Painting(1, 2), variantReader(variant).cell("idle", "SOUTH").state)
        assertEquals(CoverageState.Mirrored, variantReader(variant).cell("walk", "WEST").state)
        assertEquals(CoverageState.NotFilmed, variantReader(variant).cell("idle", "NORTH").state)

        Publisher(variantProject).publish("worker_dark", variant, folder, base = BaseProfile(profile, folder))
        assertEquals(CoverageState.Published, variantReader(variant).cell("walk", "EAST").state)

        val edited = Profile(variant = VariantOf("worker", listOf(RampSwapPair("skin", "skin"))))
        assertTrue(variantReader(edited).cell("walk", "EAST").state is CoverageState.OutOfDate)
    }

    private fun variantProject(): Project {
        val root = project.root
        root.resolve("palette").createDirectories()
        root.resolve("palette/p.hex").writeText(listOf("#000000", "#c81e1e", "#ffffff", "#0a14c8").joinToString("\n"))
        root.resolve("palette/ramps.json").writeText(
            """{ "ramps": [{ "name": "skin", "colors": ["#c81e1e"] }, { "name": "dark", "colors": ["#0a14c8"] }] }""",
        )
        return Project(root, sampleConfig().copy(palette = "palette/p.hex", afterPublish = emptyList()))
    }

    private fun variantReader(of: Project, variant: Profile) =
        CoverageReader(of, "worker_dark", variant, folder, BaseProfile(profile, folder))

    private val CoverageState.reason: String
        get() = when (this) {
            is CoverageState.OutOfDate -> reason
            is CoverageState.Unreadable -> reason
            else -> error("No reason in $this")
        }

    private fun reader(of: Profile = profile) = CoverageReader(project, "worker", of, folder)

    private fun publish(clip: Clip) {
        Publisher(project).publish("worker", profile, folder, listOf(clip))
    }

    private fun saveModel(action: String, facing: String) {
        val frame = ModelFrame(Raster.of(4, 4) { _, _ -> Argb.of(1, 2, 3) }, 80)
        ModelRepository(folder).save(ModelClip(action, facing, listOf(frame), loop = true, origin = "m.ase"))
    }

    private fun handoff(clip: Clip, durations: List<Int>, painted: Int = durations.size, x: Int = 2) {
        val canvas = project.config.canvas
        val blank = IndexedRaster(canvas.width, canvas.height)
        val frames = durations.map { HandoffFrame(it, blank, blank) }
        val sprite = HandoffMerge.merge(null, HandoffRender(palette, canvas.width, canvas.height, frames))
        val block = IndexedRaster.of(6, 10) { _, _ -> 2 }
        val withArt = sprite.frames.mapIndexed { index, frame ->
            if (index < painted) frame.copy(cels = frame.cels + AseCel(2, block, x = x, y = 45)) else frame
        }
        Files.createDirectories(folder.handoffFolder)
        AseCodec.write(sprite.copy(frames = withArt), folder.handoffFile(clip))
    }
}
