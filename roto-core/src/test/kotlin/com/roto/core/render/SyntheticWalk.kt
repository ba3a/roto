package com.roto.core.render

import com.roto.core.ase.AseCodec
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cycle.walker
import com.roto.core.draft.DraftSettings
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.mask.BrushMode
import com.roto.core.mask.BrushPoint
import com.roto.core.mask.BrushStroke
import com.roto.core.mask.MaskSettings
import com.roto.core.process.FakeProcessRunner
import com.roto.core.process.ProcessResult
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.project.Project
import com.roto.core.video.Ffmpeg
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

/**
 * A consuming project and a library under [root], set up from scratch for running the pipeline end
 * to end: the sample `roto.json` and palette, and the profile [PROFILE] with one walk clip, [CLIP],
 * whose source "video" the stand-in `ffmpeg` of [runner] decodes into [frame]s. Its sources also hold
 * the photos of [STILL], which [useStill] swaps the profile over to.
 */
internal class SyntheticWalk(val root: Path) {

    /** Stands in for `ffmpeg`, `ffprobe` and the project's `afterPublish` hook. */
    val runner = FakeProcessRunner { command -> answer(command) }
    val ffmpeg = Ffmpeg(runner)
    val project: Project = Project.open(copyProject(root.resolve("game")))
    val repository = ProfileRepository(root.resolve("library"), project.config.id)
    val folder: ProfileFolder = repository.folder(PROFILE_ID)

    init {
        repository.create(PROFILE_ID)
        folder.sourcesFolder.resolve(CLIP.source).writeBytes(SOURCE_BYTES)
        folder.sourcesFolder.resolve(STILL.source).writeBytes(SOURCE_BYTES)
        folder.sourcesFolder.resolve(WALL).writeBytes(SOURCE_BYTES + SOURCE_BYTES)
        repository.save(PROFILE_ID, PROFILE)
    }

    /** Makes the profile hold only [STILL]. */
    fun useStill() = repository.save(PROFILE_ID, PROFILE.copy(clips = listOf(STILL)))

    /** How many times frames were extracted from a video. */
    fun extractions(): Int = runner.commands.count { it.first() == "ffmpeg" && it.last().endsWith(".png") }

    /** The artist's part, scripted: every frame's draft traced into the `art` layer, unless already painted. */
    fun trace(clip: Clip) {
        val handoff = folder.handoffFile(clip)
        val sprite = AseCodec.read(handoff)
        val draft = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        if (sprite.frames.any { frame -> frame.cels.any { it.layerIndex == art } }) return
        val frames = sprite.frames.map { frame ->
            frame.copy(cels = frame.cels + frame.cels.single { it.layerIndex == draft }.copy(layerIndex = art))
        }
        AseCodec.write(sprite.copy(frames = frames), handoff)
    }

    /** `ffmpeg -version`, `ffprobe`, a photo, a range extraction (always the whole clip), or the `afterPublish` hook. */
    private fun answer(command: List<String>): ProcessResult = when {
        command.last() == "-version" -> FakeProcessRunner.success("ffmpeg version 7.1")
        command.first() == "ffprobe" -> FakeProcessRunner.success(PROBE)
        "image2pipe" in command -> {
            val photo = if (command[command.indexOf("-i") + 1].endsWith(WALL)) wall() else frame(0)
            FakeProcessRunner.success(ByteArrayOutputStream().also { PngCodec.write(photo, it) }.toByteArray())
        }
        command.first() == "ffmpeg" -> {
            val entry = Path.of(command.last()).parent
            repeat(FRAMES) { PngCodec.write(frame(it), entry.resolve(RasterSequenceCacheCodec.fileName(it))) }
            FakeProcessRunner.success()
        }
        else -> FakeProcessRunner.success("synced")
    }

    private fun copyProject(game: Path): Path {
        listOf("roto.json", "palette/faultory.gpl", "palette/ramps.json").forEach { name ->
            val target = game.resolve(name)
            Files.createDirectories(target.parent)
            javaClass.getResourceAsStream("/project/$name")!!.use { Files.copy(it, target) }
        }
        return game
    }

    companion object {
        const val PROFILE_ID = "worker"

        /** The clip: [walker] crossing a textured wall above a floor, 2 px a frame. */
        const val FRAMES = 40
        const val WIDTH = 160
        const val HEIGHT = 60
        const val FIGURE_TOP = 12
        const val FLOOR_TOP = 52

        /** The default `CycleSettings.keyFrames`, which the proposal spreads over one walk cycle. */
        const val KEY_FRAMES = 8

        private const val HEAD_ROWS = 6
        private const val TORSO_ROWS = 22
        private const val LIGHT_PER_PIXEL = 4
        private const val BYTE = 0xFF

        /** What `ffprobe` reports of the source: 30 fps, the frames' own size. */
        private const val PROBE =
            """{ "streams": [{ "width": 160, "height": 60, "avg_frame_rate": "30/1", "duration": "2" }] }"""

        private val SOURCE_BYTES = byteArrayOf(0x52, 0x4F, 0x54, 0x4F)

        val ZONES = listOf(
            Zone("head", ramp = "skin", samples = listOf(ZoneSample(frame = 0, x = 20, y = 14))),
            Zone("jacket", ramp = "suit", samples = listOf(ZoneSample(frame = 0, x = 20, y = 25))),
            Zone("trousers", ramp = "visor", samples = listOf(ZoneSample(frame = 0, x = 20, y = 36))),
        )

        /**
         * Walked across the frame; masked with lightness at full weight, since the figure is lit
         * unevenly against a darker wall; dithered; opted in to mirroring into WEST. Its key frames
         * are left to the cycle proposal.
         */
        val CLIP = Clip(
            action = "walk",
            facing = "EAST",
            source = "walk.mov",
            trim = TimeRange(500, 1_833),
            mask = MaskSettings(lightnessWeight = 1.0),
            fixes = listOf(BrushStroke(12, BrushMode.ERASE, 2, listOf(BrushPoint(40, 45), BrushPoint(50, 48)))),
            zoning = ZoneSettings(ZONES),
            draft = DraftSettings(dither = true),
            mirror = true,
        )

        private const val WALL = "wall.png"

        /** The walker's first frame as a photo, against a photo of the wall alone. */
        val STILL = Clip.still("idle", "EAST", "pose.png", WALL)
            .copy(mask = MaskSettings(lightnessWeight = 1.0), zoning = ZoneSettings(ZONES))

        val PROFILE = Profile(
            ramps = listOf("skin", "suit", "visor"),
            scale = ScaleSettings(
                bodyHeight = 48,
                marks = ScaleMarks(CLIP.id, frame = 0, headTopY = FIGURE_TOP, feetY = FLOOR_TOP - 1),
                squash = 0.9,
            ),
            clips = listOf(CLIP),
        )

        /**
         * Frame [index] of the clip: a three-colour figure — skin head, blue jacket, green trousers —
         * lit from the left, so each zone spans several shades; a faintly textured wall and a floor.
         */
        fun frame(index: Int): Raster {
            val figure = walker(index, width = WIDTH)
            val centre = 20 + 2 * index
            return Raster.of(WIDTH, HEIGHT) { x, y ->
                val row = y - FIGURE_TOP
                when {
                    row in 0 until figure.height && figure[x, row] ->
                        lit(bodyColour(row), (centre - x) * LIGHT_PER_PIXEL)
                    else -> background(x, y)
                }
            }
        }

        /** The wall and floor with nobody in front of them. */
        fun wall(): Raster = Raster.of(WIDTH, HEIGHT, ::background)

        private fun background(x: Int, y: Int): Int = when {
            y >= FLOOR_TOP -> Argb.of(60, 55, 50)
            else -> Argb.of(90 + (5 * x + 3 * y) % 7, 95 + (3 * x + 7 * y) % 5, 100)
        }

        private fun bodyColour(row: Int): Int = when {
            row < HEAD_ROWS -> Argb.of(225, 150, 115)
            row < TORSO_ROWS -> Argb.of(80, 130, 220)
            else -> Argb.of(60, 150, 120)
        }

        private fun lit(argb: Int, light: Int): Int = Argb.of(
            (Argb.red(argb) + light).coerceIn(0, BYTE),
            (Argb.green(argb) + light).coerceIn(0, BYTE),
            (Argb.blue(argb) + light).coerceIn(0, BYTE),
        )
    }
}
