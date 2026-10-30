package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.process.FakeProcessRunner
import com.roto.core.process.ProcessResult
import com.roto.core.profile.TimeRange
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FrameExtractorTest {

    private lateinit var tempRoot: Path
    private lateinit var source: Path
    private lateinit var cache: StageCache
    private var ffmpegVersion = "ffmpeg version 7.1"

    private val runner = FakeProcessRunner { command -> answer(command) }
    private val range = TimeRange(1_200, 4_800)
    private val rate = FrameRate(30, 1)

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-frames")
        source = tempRoot.resolve("IMG_0412.MOV").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        cache = StageCache(tempRoot.resolve("cache"))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `frames are extracted once and then read from the cache`() {
        val frames = FrameExtractor(Ffmpeg(runner), cache).frames(source, range, rate).get()

        assertEquals((0 until 3).map { frame(it) }, frames)
        assertEquals(frames, FrameExtractor(Ffmpeg(runner), cache).frames(source, range, rate).get())
        assertEquals(1, extractions())
    }

    @Test
    fun `trim, rate, height, source content and ffmpeg version each extract again`() {
        val extractor = FrameExtractor(Ffmpeg(runner), cache)
        extractor.frames(source, range, rate).get()

        extractor.frames(source, TimeRange(1_200, 4_900), rate).get()
        extractor.frames(source, range, FrameRate(25, 1)).get()
        extractor.frames(source, range, rate, height = 240).get()
        source.writeBytes(byteArrayOf(1, 2, 4))
        extractor.frames(source, range, rate).get()
        ffmpegVersion = "ffmpeg version 7.2"
        FrameExtractor(Ffmpeg(runner), cache).frames(source, range, rate).get()

        assertEquals(6, extractions())
    }

    @Test
    fun `the key does not depend on where the source lives`() {
        val moved = tempRoot.resolve("renamed.mov").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val extractor = FrameExtractor(Ffmpeg(runner), cache)

        val key = extractor.frames(source, range, rate).key

        assertEquals(key, extractor.frames(moved, range, rate).key)
        assertNotEquals(key, extractor.frames(source, range, rate, height = 481).key)
        assertEquals(FrameExtractor.STAGE, key.stage)
    }

    @Test
    fun `keying the frames extracts nothing until they are asked for`() {
        val frames = FrameExtractor(Ffmpeg(runner), cache).frames(source, range, rate)

        assertEquals(0, extractions())
        frames.get()
        frames.get()
        assertEquals(1, extractions())
    }

    private fun answer(command: List<String>): ProcessResult {
        if (command.last() == "-version") return FakeProcessRunner.success(ffmpegVersion)
        val pattern = command.last()
        repeat(3) { index ->
            PngCodec.write(frame(index), Path.of(pattern).resolveSibling(RasterSequenceCacheCodec.fileName(index)))
        }
        return FakeProcessRunner.success()
    }

    private fun extractions(): Int = runner.commands.count { it.last().endsWith("%06d.png") }

    private fun frame(index: Int): Raster =
        Raster.of(4, 3) { x, y -> (0xFF shl 24) or (index * 60 shl 16) or (x shl 8) or y }
}
