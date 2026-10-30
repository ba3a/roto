package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.render.ClipFootage
import com.roto.core.timeline.ClipImporter
import com.roto.core.timeline.FilmstripBuilder
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun `a sideways photo decodes upright, the same pixels every time`() {
        val photo = sidewaysPhoto("photo.jpg", "testsrc")
        val extractor = FrameExtractor(ffmpeg, StageCache(tempRoot.resolve("cache")))

        val frame = extractor.still(photo, height = 120).get().single()

        // Decoded upright: 320x240 turned on its side and scaled to 120 tall is 90 wide.
        assertEquals(90, frame.width)
        assertEquals(120, frame.height)
        val again = FrameExtractor(ffmpeg, StageCache(tempRoot.resolve("again"))).still(photo, height = 120).get()
        assertEquals(frame, again.single())
    }

    @Test
    fun `real photos import as a still against the wall`() {
        val repository = ProfileRepository(tempRoot.resolve("library"), "faultory")
        repository.create("worker")
        val importer = ClipImporter(ffmpeg, repository, "worker")
        val slot = ClipImporter.Slot("idle", "EAST")

        val clip = importer.saveStill(sidewaysPhoto("pose.jpg", "testsrc"), sidewaysPhoto("wall.jpg", "testsrc2"), slot)

        val folder = repository.folder("worker")
        val frames = ClipFootage(ffmpeg, StageCache(folder.cacheFolder), folder, clip).frames.get()
        assertEquals(1, frames.size)
        assertEquals(FrameExtractor.WORKING_HEIGHT, frames.single().height)
        assertNotNull(repository.load("worker").clip("idle", "EAST"))
    }

    private fun ffmpegRun(vararg arguments: String) {
        val command = listOf("ffmpeg", "-v", "error", "-nostdin", "-y") + arguments
        val result = SystemProcessRunner.run(command, workingDirectory = null)
        assertEquals(0, result.exitCode, result.stderr)
    }

    /**
     * A 320x240 JPEG of [pattern], its EXIF saying to turn it 90° clockwise to show, as a phone held
     * upright writes. The pattern keeps it too large for `ffmpeg` to recognise from its first bytes, so
     * it opens the way a real photo does.
     */
    private fun sidewaysPhoto(name: String, pattern: String): Path {
        val photo = tempRoot.resolve(name)
        ffmpegRun("-f", "lavfi", "-i", "$pattern=size=320x240", "-frames:v", "1", photo.toString())
        val jpeg = photo.readBytes()
        photo.writeBytes(jpeg.copyOfRange(0, 2) + exifOrientation(6) + jpeg.copyOfRange(2, jpeg.size))
        return photo
    }

    /** A JPEG APP1 segment holding only an EXIF Orientation tag. */
    private fun exifOrientation(orientation: Short): ByteArray {
        val body = ByteBuffer.allocate(32)
            .put("Exif".toByteArray()).putShort(0)
            .put("MM".toByteArray()).putShort(42).putInt(8)
            .putShort(1).putShort(0x0112).putShort(3).putInt(1).putShort(orientation).putShort(0)
            .putInt(0)
        val app1 = ByteBuffer.allocate(4).putShort(0xFFE1.toShort()).putShort((body.position() + 2).toShort())
        return app1.array() + body.array().copyOf(body.position())
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
