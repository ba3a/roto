package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.timeline.ClipImporter
import com.roto.core.timeline.FilmstripBuilder
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the real `ffmpeg` and `ffprobe` against a clip `ffmpeg` generates itself. Skipped when they
 * are not on `PATH`; everything else about [Ffmpeg] is covered by [FfmpegTest] without them.
 */
class FfmpegIntegrationTest {

    private lateinit var tempRoot: Path
    private lateinit var clip: Path
    private val ffmpeg = Ffmpeg()

    @BeforeTest
    fun setUp() {
        assumeTrue(ffmpegInstalled(), "ffmpeg and ffprobe are not on PATH")
        tempRoot = createTempDirectory("roto-ffmpeg-real")
        val upright = tempRoot.resolve("upright.mp4")
        clip = tempRoot.resolve("test.mp4")
        // Two seconds of ffmpeg's test pattern, 320x240 at 25 fps, then stored sideways the way a phone
        // stores portrait video: a display matrix saying "turn 90° clockwise to show". -display_rotation
        // counts counter-clockwise, hence -90; it needs ffmpeg 7 or later, and the older rotate tag is
        // ignored there.
        ffmpegRun(
            "-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=2",
            "-pix_fmt", "yuv420p", "-c:v", "mpeg4", "-q:v", "2", upright.toString(),
        )
        ffmpegRun("-display_rotation", "-90", "-i", upright.toString(), "-c", "copy", clip.toString())
    }

    @AfterTest
    fun tearDown() {
        if (::tempRoot.isInitialized) tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `probe reads size, duration, rate and rotation`() {
        val info = ffmpeg.probe(clip)

        assertEquals(VideoInfo(240, 320, 2_000, FrameRate(25, 1), rotation = 90), info)
    }

    @Test
    fun `a range extracts as upright frames at the working height, the same bytes every time`() {
        val first = tempRoot.resolve("first").also { it.toFile().mkdirs() }
        val second = tempRoot.resolve("second").also { it.toFile().mkdirs() }
        val range = TimeRange(500, 1_500)

        ffmpeg.extractRange(clip, range, FrameRate(25, 1), 120, first)
        ffmpeg.extractRange(clip, range, FrameRate(25, 1), 120, second)

        val frames = RasterSequenceCacheCodec.read(first)
        assertTrue(frames.size in 24..26, "${frames.size} frames")
        // Decoded upright: 240x320 scaled to 120 tall is 90 wide.
        assertEquals(90, frames.first().width)
        assertEquals(120, frames.first().height)
        first.listDirectoryEntries().forEach { frame ->
            assertContentEquals(frame.readBytes(), second.resolve(frame.fileName).readBytes())
        }
    }

    @Test
    fun `the extractor stage caches real frames`() {
        val extractor = FrameExtractor(ffmpeg, StageCache(tempRoot.resolve("cache")))

        val frames = extractor.frames(clip, TimeRange(0, 400), FrameRate(25, 1), height = 60).get()

        assertEquals(frames, extractor.frames(clip, TimeRange(0, 400), FrameRate(25, 1), height = 60).get())
        assertEquals(60, frames.first().height)
    }

    @Test
    fun `a thumbnail is one frame at the requested height`() {
        assertEquals(48, ffmpeg.thumbnail(clip, 1_000, 48).height)
    }

    @Test
    fun `a real video imports as a clip, with its filmstrip and frames`() {
        val repository = ProfileRepository(tempRoot.resolve("library"), "faultory")
        repository.create("worker")
        val folder = repository.folder("worker")
        val range = TimeRange(400, 800)

        val clip = ClipImporter(ffmpeg, repository, "worker").save(clip, ClipImporter.Slot("walk", "EAST"), range)

        val source = folder.sourceFile(clip)
        val cache = StageCache(folder.cacheFolder)
        val strip = FilmstripBuilder(ffmpeg, cache).strip(source, ffmpeg.probe(source).durationMs).get()
        assertEquals(FilmstripBuilder.FRAMES, strip.size)
        assertEquals(FilmstripBuilder.THUMBNAIL_HEIGHT, strip.first().height)
        assertEquals(10, FrameExtractor(ffmpeg, cache).frames(source, range, FrameRate(25, 1)).get().size)
    }

    private fun ffmpegRun(vararg arguments: String) {
        val command = listOf("ffmpeg", "-v", "error", "-nostdin", "-y") + arguments
        val result = SystemProcessRunner.run(command, workingDirectory = null)
        assertEquals(0, result.exitCode, result.stderr)
    }

    private fun ffmpegInstalled(): Boolean =
        listOf("ffmpeg", "ffprobe").all { program ->
            try {
                SystemProcessRunner.run(listOf(program, "-version"), workingDirectory = null).exitCode == 0
            } catch (ignored: IOException) {
                false
            }
        }
}
