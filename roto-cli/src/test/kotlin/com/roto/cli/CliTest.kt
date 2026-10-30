package com.roto.cli

import com.roto.core.ase.AseCodec
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.mask.MaskSettings
import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTest {

    private lateinit var tempRoot: Path
    private lateinit var game: Path
    private lateinit var library: Path
    private lateinit var repository: ProfileRepository

    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()
    private val commands = mutableListOf<List<String>>()

    /** Stands in for `ffmpeg`, `ffprobe` and the project's `sync` hook. */
    private val runner = ProcessRunner { command, _ ->
        commands += command
        when {
            command.last() == "-version" -> result("ffmpeg version 7.1")
            command.first() == "ffprobe" -> result(PROBE)
            command.first() == "ffmpeg" -> {
                val entry = Path.of(command.last()).parent
                repeat(FRAMES) { PngCodec.write(frame(it), entry.resolve(RasterSequenceCacheCodec.fileName(it))) }
                result("")
            }
            else -> result("synced ${command.last()}\n")
        }
    }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-cli")
        game = tempRoot.resolve("game").createDirectories()
        game.resolve("roto.json").writeText(ROTO_JSON)
        game.resolve("palette.gpl").writeText(PALETTE)
        game.resolve("ramps.json").writeText(RAMPS)
        library = tempRoot.resolve("library")
        repository = ProfileRepository(library, "demo")
        repository.create("hero")
        repository.folder("hero").sourcesFolder.resolve(CLIP.source).writeBytes(byteArrayOf(1, 2, 3))
        repository.save("hero", PROFILE)
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `render writes the handoff, and publish ships the art and runs the hook`() {
        assertEquals(Cli.SUCCESS, run("render", "hero"), errors())
        val handoff = repository.folder("hero").handoffFile(CLIP)
        assertContains(output(), "Rendered $handoff")

        paint(handoff)
        assertEquals(Cli.SUCCESS, run("publish", "hero"), errors())

        val frame = game.resolve("art/hero/walk_east/000.png")
        assertTrue(Files.isRegularFile(frame))
        assertContains(output(), "Published $frame")
        assertContains(output(), "synced hero")
        assertEquals(listOf("sync", "hero"), commands.last())
    }

    @Test
    fun `the project defaults to the working folder and the library to the environment`() {
        val cli = Cli(print(out), print(err), mapOf(Arguments.LIBRARY_VARIABLE to library.toString()), game, runner)

        assertEquals(Cli.SUCCESS, cli.run(listOf("render", "hero")), errors())
    }

    @Test
    fun `publishing before the art is painted fails with the reason`() {
        run("render", "hero")

        assertEquals(Cli.FAILURE, run("publish", "hero"))
        assertContains(errors(), "has no art yet")
    }

    @Test
    fun `a missing project, profile or clip fails with the reason`() {
        assertEquals(Cli.FAILURE, run("render", "hero", project = tempRoot))
        assertContains(errors(), "holds no roto.json")

        assertEquals(Cli.FAILURE, run("render", "villain"))
        assertContains(errors(), "No profile 'villain'")

        repository.save("hero", Profile())
        assertEquals(Cli.FAILURE, run("render", "hero"))
        assertContains(errors(), "nothing to render")
    }

    @Test
    fun `a malformed command line prints the usage and exits with the usage code`() {
        assertEquals(Cli.USAGE, Cli(print(out), print(err), emptyMap(), game, runner).run(listOf("draw")))
        assertContains(errors(), "Unknown command 'draw'")
        assertContains(errors(), "Usage: roto")
    }

    @Test
    fun `help prints the usage and succeeds`() {
        assertEquals(Cli.SUCCESS, Cli(print(out), print(err), emptyMap(), game, runner).run(listOf("--help")))
        assertContains(output(), "Usage: roto")
    }

    private fun run(command: String, profile: String, project: Path = game): Int =
        Cli(print(out), print(err), emptyMap(), tempRoot, runner)
            .run(listOf(command, profile, "--project", project.toString(), "--library", library.toString()))

    /** The artist's part: every frame's draft copied into the `art` layer. */
    private fun paint(handoff: Path) {
        val sprite = AseCodec.read(handoff)
        val draft = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        val frames = sprite.frames.map { frame ->
            frame.copy(cels = frame.cels + frame.cels.single { it.layerIndex == draft }.copy(layerIndex = art))
        }
        AseCodec.write(sprite.copy(frames = frames), handoff)
    }

    private fun output() = out.toString(Charsets.UTF_8)

    private fun errors() = err.toString(Charsets.UTF_8)

    private fun print(stream: ByteArrayOutputStream) = PrintStream(stream, true, Charsets.UTF_8)

    private fun result(stdout: String) = ProcessResult(0, stdout.toByteArray(Charsets.UTF_8), "")

    private companion object {
        const val FRAMES = 12
        const val WIDTH = 80
        const val HEIGHT = 50
        const val TOP = 10
        const val BOTTOM = 39

        const val PROBE =
            """{ "streams": [{ "width": 80, "height": 50, "avg_frame_rate": "30/1", "duration": "1" }] }"""

        const val ROTO_JSON = """{
  "id": "demo",
  "palette": "palette.gpl",
  "ramps": "ramps.json",
  "facings": ["EAST", "WEST"],
  "actions": ["walk"],
  "canvas": { "width": 20, "height": 40, "groundRow": 39 },
  "output": "art/{profile}/{action}_{facing:lower}/{frame:000}.png",
  "manifest": "art/{profile}/{action}_{facing:lower}/clip.json",
  "afterPublish": ["sync", "{profile}"]
}
"""

        const val PALETTE =
            "GIMP Palette\nName: Demo\n 90  58  46\tskin dark\n160 103  75\tskin\n224 169 138\tskin light\n"

        const val RAMPS = """{ "ramps": [{ "name": "skin", "colors": ["#5a3a2e", "#a0674b", "#e0a98a"] }] }"""

        /** Performed once across the frame, so its key frames are spread over the whole clip. */
        val CLIP = Clip(
            action = "walk",
            facing = "EAST",
            source = "walk.mov",
            trim = TimeRange(0, 400),
            mask = MaskSettings(lightnessWeight = 1.0),
            zoning = ZoneSettings(listOf(Zone("body", "skin", listOf(ZoneSample(frame = 0, x = 14, y = 20))))),
            loop = false,
        )

        val PROFILE = Profile(
            ramps = listOf("skin"),
            scale = ScaleSettings(bodyHeight = 30, marks = ScaleMarks(CLIP.id, 0, headTopY = TOP, feetY = BOTTOM)),
            clips = listOf(CLIP),
        )

        /** Frame [index]: a block lit from the left, moving 4 px a frame across a dark wall. */
        fun frame(index: Int): Raster {
            val left = 10 + 4 * index
            return Raster.of(WIDTH, HEIGHT) { x, y ->
                if (x in left until left + 8 && y in TOP..BOTTOM) {
                    Argb.of(230 - 10 * (x - left), 160 - 8 * (x - left), 120 - 6 * (x - left))
                } else {
                    Argb.of(70, 75, 80)
                }
            }
        }
    }
}
