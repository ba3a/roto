package com.roto.core.publish

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseCodec
import com.roto.core.handoff.HandoffFrame
import com.roto.core.handoff.HandoffMerge
import com.roto.core.handoff.HandoffRender
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.PngCodec
import com.roto.core.palette.Palette
import com.roto.core.process.FakeProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.TimeRange
import com.roto.core.project.Project
import com.roto.core.project.sampleConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublisherTest {

    private lateinit var tempRoot: Path
    private lateinit var project: Project
    private lateinit var folder: ProfileFolder
    private val runner = FakeProcessRunner { FakeProcessRunner.success("synced") }

    private val red = Argb.of(200, 30, 30)
    private val palette = Palette(listOf(Argb.of(0, 0, 0), red, Argb.of(255, 255, 255)))

    private val walkEast = Clip("walk", "EAST", "IMG_0412.MOV", TimeRange(0, 1_000), mirror = true)
    private val idleSouth = Clip("idle", "SOUTH", "IMG_0420.MOV", TimeRange(0, 1_000), loop = false)
    private val profile = Profile(clips = listOf(walkEast, idleSouth))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-publish")
        project = Project(tempRoot.resolve("game"), sampleConfig())
        folder = ProfileFolder(tempRoot.resolve("library").resolve("faultory").resolve("worker"))
        handoff(walkEast, durations = listOf(120, 80))
        handoff(idleSouth, durations = listOf(500))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `frames and manifests land where roto json says, with the art's colours and durations`() {
        val report = Publisher(project, runner).publish("worker", profile, folder)

        val clipFolder = project.root.resolve("raw-art/worker/walk_east")
        val frame = PngCodec.read(clipFolder.resolve("000.png"))
        assertEquals(red, frame[3, 50])
        assertEquals(Argb.TRANSPARENT, frame[30, 50])
        val manifest = ClipManifestCodec.read(clipFolder.resolve("clip.json"))
        assertEquals(
            ClipManifest(
                action = "walk",
                facing = "EAST",
                source = "walk_EAST",
                mirrored = false,
                loop = true,
                frames = listOf(
                    ManifestFrame("raw-art/worker/walk_east/000.png", 120),
                    ManifestFrame("raw-art/worker/walk_east/001.png", 80),
                ),
            ),
            manifest,
        )
        assertEquals(false, ClipManifestCodec.read(project.root.resolve("raw-art/worker/idle_south/clip.json")).loop)
        assertEquals(2 + 1 + 2 + 1 + 1 + 1, report.files.size)
    }

    @Test
    fun `an opted-in clip is also published mirrored into its pair`() {
        Publisher(project, runner).publish("worker", profile, folder)

        val west = project.root.resolve("raw-art/worker/walk_west")
        val east = PngCodec.read(project.root.resolve("raw-art/worker/walk_east/000.png"))
        assertEquals(Mirror.flip(east), PngCodec.read(west.resolve("000.png")))
        val manifest = ClipManifestCodec.read(west.resolve("clip.json"))
        assertEquals("WEST" to "walk_EAST", manifest.facing to manifest.source)
        assertTrue(manifest.mirrored)
    }

    @Test
    fun `afterPublish runs last in the project root, its program found there`() {
        val report = Publisher(project, runner).publish("worker", profile, folder)

        val expectedProgram = project.root.resolve("gradlew.bat").toString()
        assertEquals(listOf(expectedProgram, ":editor:syncSkin", "--args=worker"), runner.commands.single())
        assertEquals(project.root, runner.directories.single())
        assertEquals("synced", report.hook?.stdout?.toString(Charsets.UTF_8))
    }

    @Test
    fun `a failing afterPublish is reported, the frames stay published`() {
        val failing = FakeProcessRunner { FakeProcessRunner.failure(1, "BUILD FAILED\nskin worker has no idle\n") }

        val error = assertFailsWith<PublishException> { Publisher(project, failing).publish("worker", profile, folder) }

        assertContains(error.message.orEmpty(), "exit code 1")
        assertContains(error.message.orEmpty(), "skin worker has no idle")
        assertTrue(project.root.resolve("raw-art/worker/walk_east/000.png").exists())
    }

    @Test
    fun `without an afterPublish command nothing runs`() {
        val quiet = Project(project.root, sampleConfig().copy(afterPublish = emptyList()))

        assertNull(Publisher(quiet, runner).publish("worker", profile, folder).hook)
        assertEquals(emptyList(), runner.commands)
    }

    @Test
    fun `frames a longer earlier publish left behind are removed`() {
        val clipFolder = project.root.resolve("raw-art/worker/walk_east").createDirectories()
        listOf("002.png", "003.png", "notes.txt").forEach { clipFolder.resolve(it).writeText("old") }

        Publisher(project, runner).publish("worker", profile, folder)

        assertFalse(clipFolder.resolve("002.png").exists())
        assertFalse(clipFolder.resolve("003.png").exists())
        assertTrue(clipFolder.resolve("notes.txt").exists())
    }

    @Test
    fun `unfinished or impossible publishes stop before anything runs`() {
        handoff(idleSouth, durations = listOf(500, 500), paintedFrames = 1)
        val publisher = Publisher(project, runner)
        val unpainted = assertFailsWith<PublishException> { publisher.publish("worker", profile, folder) }
        assertContains(unpainted.message.orEmpty(), "Frame 2 of idle_SOUTH has no art yet")

        val unrendered = Clip("carry", "EAST", "IMG_0500.MOV", TimeRange(0, 1_000))
        val missing = assertFailsWith<PublishException> {
            publisher.publish("worker", profile, folder, listOf(unrendered))
        }
        assertContains(missing.message.orEmpty(), "render it first")

        val filmedWest = profile.copy(clips = profile.clips + Clip("walk", "WEST", "IMG_0414.MOV", TimeRange(0, 1_000)))
        assertFailsWith<PublishException> { publisher.publish("worker", filmedWest, folder, listOf(walkEast)) }

        val northMirror = Clip("walk", "NORTH", "IMG_0413.MOV", TimeRange(0, 1_000), mirror = true)
        assertFailsWith<PublishException> { publisher.publish("worker", profile, folder, listOf(northMirror)) }
        assertEquals(emptyList(), runner.commands)
    }

    private fun blank() = IndexedRaster(project.config.canvas.width, project.config.canvas.height)

    /** A handoff for [clip] with [durations], its first [paintedFrames] frames painted: a red block left of centre. */
    private fun handoff(clip: Clip, durations: List<Int>, paintedFrames: Int = durations.size) {
        val canvas = project.config.canvas
        val render = HandoffRender(
            palette,
            canvas.width,
            canvas.height,
            durations.map { HandoffFrame(it, blank(), blank()) },
        )
        val sprite = HandoffMerge.merge(null, render)
        val block = IndexedRaster.of(6, 10) { _, _ -> 2 }
        val frames = sprite.frames.mapIndexed { index, frame ->
            if (index < paintedFrames) frame.copy(cels = frame.cels + AseCel(2, block, x = 2, y = 45)) else frame
        }
        Files.createDirectories(folder.handoffFolder)
        AseCodec.write(sprite.copy(frames = frames), folder.handoffFile(clip))
    }
}
