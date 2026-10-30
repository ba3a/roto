package com.roto.core.timeline

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.mask.BrushMode
import com.roto.core.mask.BrushPoint
import com.roto.core.mask.BrushStroke
import com.roto.core.process.FakeProcessRunner
import com.roto.core.process.ProcessResult
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.template.RelativePath
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import com.roto.core.zone.ZoneStroke
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClipImportTest {

    private lateinit var tempRoot: Path
    private lateinit var video: Path
    private lateinit var repository: ProfileRepository

    private val runner = FakeProcessRunner { command -> answer(command) }
    private val ffmpeg = Ffmpeg(runner)
    private val folder get() = repository.folder("worker")
    private val range = TimeRange(1_000, 2_000)
    private val walkEast = ClipImporter.Slot("walk", "EAST")

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-import")
        video = tempRoot.resolve("phone").createDirectories().resolve("IMG_0412.MOV")
        video.writeBytes(byteArrayOf(1, 2, 3))
        repository = ProfileRepository(tempRoot.resolve("library"), "faultory")
        repository.create("worker")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a video is copied whole into sources and the original stays`() {
        assertEquals("IMG_0412.MOV", SourceImport.import(video, folder))

        assertContentEquals(byteArrayOf(1, 2, 3), folder.sourcesFolder.resolve("IMG_0412.MOV").readBytes())
        assertContentEquals(byteArrayOf(1, 2, 3), video.readBytes())
    }

    @Test
    fun `an identical copy is reused and a different file of the same name is not replaced`() {
        SourceImport.import(video, folder)
        assertEquals("IMG_0412.MOV", SourceImport.import(video, folder))

        val other = tempRoot.resolve("IMG_0412.MOV").also { it.writeBytes(byteArrayOf(9)) }
        assertEquals("IMG_0412-2.MOV", SourceImport.import(other, folder))
        assertContentEquals(byteArrayOf(1, 2, 3), folder.sourcesFolder.resolve("IMG_0412.MOV").readBytes())
        assertContentEquals(byteArrayOf(9), folder.sourcesFolder.resolve("IMG_0412-2.MOV").readBytes())
    }

    @Test
    fun `a file already in sources stays where it is, subfolder included`() {
        val inside = folder.sourcesFolder.resolve("idle").createDirectories().resolve("a.mov")
        inside.writeBytes(byteArrayOf(4))

        assertEquals("idle/a.mov", SourceImport.import(inside, folder))
        assertEquals(1, Files.list(folder.sourcesFolder).use { it.count() })
    }

    @Test
    fun `a name a clip could not hold is made safe`() {
        assertEquals("a_b_.mov", RelativePath.safeSegment("a:b?.mov"))
        RelativePath.validate(RelativePath.safeSegment("a:b?.mov"), "source")
        assertEquals("_", RelativePath.safeSegment(".."))
    }

    @Test
    fun `saving adds the clip to the profile and extracts its frames`() {
        val clip = importer().save(video, walkEast, range)

        assertEquals(Clip("walk", "EAST", "IMG_0412.MOV", range), clip)
        assertEquals(listOf(clip), repository.load("worker").clips)
        assertEquals(1, extractions())
        assertTrue(folder.sourcesFolder.resolve("IMG_0412.MOV").exists())
        val frames = FrameExtractor(ffmpeg, StageCache(folder.cacheFolder))
            .frames(folder.sourceFile(clip), range, ffmpeg.probe(folder.sourceFile(clip)).frameRate).get()
        assertEquals(listOf(frame()), frames)
        assertEquals(1, extractions())
    }

    @Test
    fun `saving over a filmed clip recuts it and keeps its settings and its neighbours`() {
        val idle = Clip("idle", "EAST", "idle.mov", range)
        val walk = Clip("walk", "EAST", "IMG_0412.MOV", range, loop = false)
        repository.save("worker", Profile(clips = listOf(walk, idle)))

        val saved = importer().save(video, walkEast, TimeRange(0, 500))

        assertEquals(listOf(walk.copy(trim = TimeRange(0, 500)), idle), repository.load("worker").clips)
        assertEquals(false, saved.loop)
    }

    @Test
    fun `a new cut drops what counted the old frames`() {
        val clip = Clip(
            "walk",
            "EAST",
            "a.mov",
            range,
            plate = PlateSource.EmptyRange(TimeRange(0, 500)),
            markers = listOf(0, 5),
            fixes = listOf(BrushStroke(0, BrushMode.ADD, radius = 1, points = listOf(BrushPoint(1, 1)))),
            zoning = ZoneSettings(
                listOf(Zone("skin", "skin", listOf(ZoneSample(3, 1, 1)))),
                lightnessWeight = 0.7,
                fixes = listOf(ZoneStroke(0, "skin", 1, listOf(BrushPoint(1, 1)))),
            ),
        )
        assertTrue(clip.hasFrameWork)

        val trimmed = clip.recut("a.mov", TimeRange(1_000, 1_500))
        assertFalse(trimmed.hasFrameWork)
        assertNull(trimmed.markers)
        assertTrue(trimmed.fixes.isEmpty())
        assertTrue(trimmed.zoning.zones.isEmpty())
        assertEquals(0.7, trimmed.zoning.lightnessWeight)
        assertEquals(clip.plate, trimmed.plate)

        assertEquals(PlateSource.ClipMedian, clip.recut("b.mov", range).plate)
        assertSame(clip, clip.recut("a.mov", range))
    }

    @Test
    fun `the filmstrip samples the centre of each slice and is decoded once`() {
        assertEquals(listOf(100L, 300L, 500L, 700L, 900L), FilmstripBuilder.centres(1_000, 5))

        val cache = StageCache(tempRoot.resolve("cache"))
        val strip = FilmstripBuilder(ffmpeg, cache).strip(video, 1_000, count = 3, height = 2).get()
        val thumbnails = { runner.commands.count { it.contains("image2pipe") } }

        assertEquals(listOf(frame(), frame(), frame()), strip)
        assertEquals(3, thumbnails())
        assertEquals(strip, FilmstripBuilder(ffmpeg, cache).strip(video, 1_000, count = 3, height = 2).get())
        assertEquals(3, thumbnails())
    }

    private fun importer() = ClipImporter(ffmpeg, repository, "worker")

    private fun extractions(): Int =
        runner.commands.count { it.last().endsWith(RasterSequenceCacheCodec.PRINTF_PATTERN) }

    private fun answer(command: List<String>): ProcessResult = when {
        command.last() == "-version" -> FakeProcessRunner.success("ffmpeg version 7.1")
        command.first() == "ffprobe" -> FakeProcessRunner.success(PROBE)
        command.contains("image2pipe") -> FakeProcessRunner.success(
            ByteArrayOutputStream().also { PngCodec.write(frame(), it) }.toByteArray(),
        )
        else -> {
            PngCodec.write(frame(), Path.of(command.last()).resolveSibling(RasterSequenceCacheCodec.fileName(0)))
            FakeProcessRunner.success()
        }
    }

    private fun frame(): Raster = Raster.of(2, 2) { x, y -> (0xFF shl 24) or (x shl 8) or y }

    private companion object {
        const val PROBE = """{ "streams": [{ "width": 2, "height": 2, "avg_frame_rate": "30/1", "duration": "5" }] }"""
    }
}
