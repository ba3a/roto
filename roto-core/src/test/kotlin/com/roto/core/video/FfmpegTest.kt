package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.process.FakeProcessRunner
import com.roto.core.profile.TimeRange
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FfmpegTest {

    private lateinit var tempRoot: Path
    private val source = Path.of("sources", "IMG_0412.MOV")

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-ffmpeg")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `probe asks ffprobe for the first video stream as JSON`() {
        val runner = FakeProcessRunner {
            FakeProcessRunner.success(
                """{ "streams": [{ "width": 1920, "height": 1080, "avg_frame_rate": "30/1", "duration": "2.5" }] }""",
            )
        }

        val info = Ffmpeg(runner).probe(source)

        assertEquals(VideoInfo(1920, 1080, 2_500, FrameRate(30, 1), rotation = 0), info)
        assertEquals(
            listOf(
                "ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
                "-select_streams", "v:0", source.toString(),
            ),
            runner.commands.single(),
        )
    }

    @Test
    fun `a probe ffprobe cannot describe names the file`() {
        val runner = FakeProcessRunner { FakeProcessRunner.success("""{ "streams": [] }""") }

        val error = assertFailsWith<VideoToolException> { Ffmpeg(runner).probe(source) }

        assertContains(error.message.orEmpty(), "IMG_0412.MOV")
        assertContains(error.message.orEmpty(), "no video stream")
    }

    @Test
    fun `extracting a range seeks, trims, resamples and scales exactly`() {
        val runner = FakeProcessRunner { command ->
            Path.of(command.last()).resolveSibling(RasterSequenceCacheCodec.fileName(0)).writeText("frame")
            FakeProcessRunner.success()
        }

        Ffmpeg(runner).extractRange(source, TimeRange(1_200, 4_805), FrameRate(30000, 1001), 480, tempRoot)

        assertEquals(
            listOf(
                "ffmpeg", "-v", "error", "-nostdin", "-y", "-ss", "1.200", "-i", source.toString(),
                "-t", "3.605", "-an", "-sn", "-dn",
                "-vf", "fps=30000/1001,scale=-1:480:flags=area+accurate_rnd+full_chroma_int+bitexact,format=rgb24",
                "-start_number", "0", tempRoot.resolve("%06d.png").toString(),
            ),
            runner.commands.single(),
        )
    }

    @Test
    fun `a range ffmpeg extracts nothing from is an error`() {
        val runner = FakeProcessRunner { FakeProcessRunner.success() }

        val error = assertFailsWith<VideoToolException> {
            Ffmpeg(runner).extractRange(source, TimeRange(90_000, 91_000), FrameRate(30, 1), 480, tempRoot)
        }

        assertContains(error.message.orEmpty(), "no frames")
    }

    @Test
    fun `a thumbnail is decoded from ffmpeg's stdout`() {
        val frame = Raster.of(5, 3) { x, y -> (0xFF shl 24) or (x * 50 shl 16) or (y * 80) }
        val png = ByteArrayOutputStream().also { PngCodec.write(frame, it) }.toByteArray()
        val runner = FakeProcessRunner { FakeProcessRunner.success(png) }

        assertEquals(frame, Ffmpeg(runner).thumbnail(source, 65_007, 64))
        assertEquals(
            listOf(
                "ffmpeg", "-v", "error", "-nostdin", "-ss", "65.007", "-i", source.toString(),
                "-an", "-sn", "-dn", "-frames:v", "1",
                "-vf", "scale=-1:64:flags=area+accurate_rnd+full_chroma_int+bitexact,format=rgb24",
                "-f", "image2pipe", "-c:v", "png", "-",
            ),
            runner.commands.single(),
        )
    }

    @Test
    fun `a thumbnail past the end is an error`() {
        val runner = FakeProcessRunner { FakeProcessRunner.success() }

        assertFailsWith<VideoToolException> { Ffmpeg(runner).thumbnail(source, 99_000, 64) }
    }

    @Test
    fun `version is the first line of ffmpeg -version, asked for once`() {
        val runner = FakeProcessRunner {
            FakeProcessRunner.success("ffmpeg version 7.1 Copyright (c) 2000-2024\nbuilt with gcc 14\n")
        }
        val ffmpeg = Ffmpeg(runner)

        assertEquals("ffmpeg version 7.1 Copyright (c) 2000-2024", ffmpeg.version)
        assertEquals("ffmpeg version 7.1 Copyright (c) 2000-2024", ffmpeg.version)
        assertEquals(listOf(listOf("ffmpeg", "-version")), runner.commands)
    }

    @Test
    fun `a failing program reports its exit code and the end of its stderr`() {
        val runner = FakeProcessRunner {
            FakeProcessRunner.failure(1, "line 1\nline 2\n\nIMG_0412.MOV: Invalid data found when processing input\n")
        }

        val error = assertFailsWith<VideoToolException> { Ffmpeg(runner).probe(source) }

        assertContains(error.message.orEmpty(), "ffprobe failed with exit code 1")
        assertContains(error.message.orEmpty(), "Invalid data found")
    }

    @Test
    fun `a program that cannot be started points at PATH`() {
        val runner = FakeProcessRunner { throw IOException("Cannot run program \"ffprobe\"") }

        val error = assertFailsWith<VideoToolException> { Ffmpeg(runner).probe(source) }

        assertContains(error.message.orEmpty(), "PATH")
    }

    @Test
    fun `configured program names are used`() {
        val runner = FakeProcessRunner { FakeProcessRunner.success("ffmpeg version 6.0") }

        Ffmpeg(runner, ffmpeg = "/opt/ffmpeg/bin/ffmpeg").version

        assertEquals("/opt/ffmpeg/bin/ffmpeg", runner.commands.single().first())
    }
}
