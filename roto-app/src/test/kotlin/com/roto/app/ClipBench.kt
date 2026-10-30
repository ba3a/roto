package com.roto.app

import com.roto.core.cache.RasterSequenceCacheCodec
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
import com.roto.core.settings.SettingsStore
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * A consuming project and a library under [root], with the profile `hero` holding [CLIP] and [home]
 * open on it. A stand-in `ffmpeg` decodes the clip's source into [frame]s and a photo through [photo];
 * [probe] answers as `ffprobe`. Any other program goes to [program], and is remembered in [commands];
 * [systemEditor] stands in for whatever the system opens a file with.
 */
internal class ClipBench(root: Path) {

    var probe: () -> ProcessResult = { result(PROBE) }
    var program: (List<String>) -> ProcessResult = { result("") }
    var photo: (String) -> ProcessResult = { png(frame(PHOTO_FRAME)) }
    var systemEditor = Editor { }

    val commands = mutableListOf<List<String>>()

    private val runner = ProcessRunner { command, _ ->
        when {
            command.last() == "-version" -> result("ffmpeg version 7.1")
            command.first() == "ffprobe" -> probe()
            "image2pipe" in command -> photo(Path.of(command[command.indexOf("-i") + 1]).fileName.toString())
            command.first() == "ffmpeg" -> {
                val entry = Path.of(command.last()).parent
                repeat(FRAMES) { PngCodec.write(frame(it), entry.resolve(RasterSequenceCacheCodec.fileName(it))) }
                result("")
            }
            else -> {
                commands += command
                program(command)
            }
        }
    }

    val game: Path = root.resolve("game").createDirectories()
    val repository: ProfileRepository
    val home: HomeModel

    init {
        game.resolve("roto.json").writeText(ROTO_JSON)
        game.resolve("palette.gpl").writeText(PALETTE)
        game.resolve("ramps.json").writeText(RAMPS)
        val library = root.resolve("library")
        repository = ProfileRepository(library, "demo")
        home = HomeModel(SettingsStore(root.resolve("settings.json")), runner) { systemEditor.open(it) }.apply {
            openProject(game)
            chooseLibrary(library)
            createProfile("hero")
        }
        repository.folder("hero").sourcesFolder.resolve(CLIP.source).writeBytes(byteArrayOf(1, 2, 3))
        save(Profile(clips = listOf(CLIP)))
    }

    /** Stores [profile] as `hero`, and has [home] read it. */
    fun save(profile: Profile) {
        repository.save("hero", profile)
        home.refresh()
    }

    /** The clip as `profile.json` holds it now. */
    fun storedClip(): Clip = checkNotNull(repository.load("hero").clip(CLIP.action, CLIP.facing))

    companion object {
        const val FRAMES = 12
        const val LEFT = 10

        /** Which [frame] a photo shows unless told otherwise. */
        const val PHOTO_FRAME = 3

        /** The even proposal over [FRAMES] frames, for a clip that does not loop. */
        val PROPOSAL = listOf(0, 2, 3, 5, 6, 8, 9, 11, 12)

        const val PROBE =
            """{ "streams": [{ "width": 80, "height": 50, "avg_frame_rate": "30/1", "duration": "4" }] }"""

        /** Left to the even proposal, so its key frames are known without a walk cycle. */
        val CLIP = Clip(
            "walk",
            "EAST",
            "walk.mov",
            TimeRange(0, 400),
            mask = MaskSettings(lightnessWeight = 1.0),
            loop = false,
        )

        /** A program's result, exiting with [code]. */
        fun result(stdout: String, code: Int = 0) = ProcessResult(code, stdout.toByteArray(Charsets.UTF_8), "")

        /** `ffmpeg`'s answer when it pipes [raster] out as a PNG. */
        fun png(raster: Raster): ProcessResult =
            ProcessResult(0, ByteArrayOutputStream().also { PngCodec.write(raster, it) }.toByteArray(), "")

        /** Frame [index]: an orange block, lit from the left, moving 4 px a frame across a dark wall. */
        fun frame(index: Int): Raster {
            val left = LEFT + 4 * index
            return Raster.of(80, 50) { x, y ->
                if (x in left until left + 8 && y in 10..39) {
                    Argb.of(230 - 8 * (x - left), 160 - 6 * (x - left), 120 - 4 * (x - left))
                } else {
                    WALL
                }
            }
        }

        /** The dark wall alone. */
        fun wall(): Raster = Raster.of(80, 50) { _, _ -> WALL }

        private val WALL = Argb.of(70, 75, 80)

        private const val ROTO_JSON = """{
  "id": "demo",
  "palette": "palette.gpl",
  "ramps": "ramps.json",
  "facings": ["EAST", "WEST"],
  "mirrorPairs": [["EAST", "WEST"]],
  "actions": ["walk"],
  "canvas": { "width": 20, "height": 40, "groundRow": 39 },
  "output": "art/{profile}/{action}_{facing:lower}/{frame:000}.png",
  "manifest": "art/{profile}/{action}_{facing:lower}/clip.json",
  "afterPublish": ["sync", "{profile}"]
}
"""

        private const val PALETTE =
            "GIMP Palette\nName: Demo\n 90  58  46\tskin dark\n160 103  75\tskin\n224 169 138\tskin light\n" +
                " 30  42  74\tsuit dark\n 90 134 200\tsuit light\n"

        private const val RAMPS =
            """{ "ramps": [
  { "name": "skin", "colors": ["#5a3a2e", "#a0674b", "#e0a98a"] },
  { "name": "suit", "colors": ["#1e2a4a", "#5a86c8"] }
] }"""
    }
}
