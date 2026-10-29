package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.TimeRange
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
        clip = tempRoot.resolve("test.mp4")
        // Two seconds of ffmpeg's test pattern: 320x240, 25 fps, stored sideways with a 90° display rotation.
        val result = SystemProcessRunner.run(
            listOf(
                "ffmpeg", "-v", "error", "-nostdin", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=2",
                "-pix_fmt", "yuv420p", "-c:v", "mpeg4", "-q:v", "2", "-metadata:s:v", "rotate=90", clip.toString(),
            ),
        )
        assertEquals(0, result.exitCode, result.stderr)
    }

    @AfterTest
    fun tearDown() {
        if (::tempRoot.isInitialized) tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `probe reads size, duration, rate and rotation`() {
        val info = ffmpeg.probe(clip)

        assertEquals(FrameRate(25, 1), info.frameRate)
        assertEquals(2_000, info.durationMs)
        assertTrue(info.rotation in listOf(0, 90), "rotation ${info.rotation}")
        if (info.rotation == 90) assertEquals(240 to 320, info.width to info.height)
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
        val upright = ffmpeg.probe(clip).width
        assertEquals(120, frames.first().height)
        assertEquals(if (upright == 240) 90 else 160, frames.first().width)
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

    private fun ffmpegInstalled(): Boolean =
        listOf("ffmpeg", "ffprobe").all { program ->
            try {
                SystemProcessRunner.run(listOf(program, "-version")).exitCode == 0
            } catch (ignored: IOException) {
                false
            }
        }
}
