package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
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
        val frames = FrameExtractor(Ffmpeg(runner), cache).extract(source, range, rate)

        assertEquals((0 until 3).map { frame(it) }, frames)
        assertEquals(frames, FrameExtractor(Ffmpeg(runner), cache).extract(source, range, rate))
        assertEquals(1, extractions())
    }

    @Test
    fun `trim, rate, height, source content and ffmpeg version each extract again`() {
        val extractor = FrameExtractor(Ffmpeg(runner), cache)
        extractor.extract(source, range, rate)

        extractor.extract(source, TimeRange(1_200, 4_900), rate)
        extractor.extract(source, range, FrameRate(25, 1))
        extractor.extract(source, range, rate, height = 240)
        source.writeBytes(byteArrayOf(1, 2, 4))
        extractor.extract(source, range, rate)
        ffmpegVersion = "ffmpeg version 7.2"
        FrameExtractor(Ffmpeg(runner), cache).extract(source, range, rate)

        assertEquals(6, extractions())
    }

    @Test
    fun `the key does not depend on where the source lives`() {
        val moved = tempRoot.resolve("renamed.mov").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val extractor = FrameExtractor(Ffmpeg(runner), cache)

        assertEquals(extractor.key(source, range, rate), extractor.key(moved, range, rate))
        assertNotEquals(extractor.key(source, range, rate), extractor.key(source, range, rate, height = 481))
        assertEquals(FrameExtractor.STAGE, extractor.key(source, range, rate).stage)
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
