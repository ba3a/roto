package com.roto.app

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.settings.SettingsStore
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrimModelTest {

    private lateinit var tempRoot: Path
    private lateinit var video: Path
    private lateinit var repository: ProfileRepository
    private lateinit var home: HomeModel
    private var probe: () -> ProcessResult = { result(PROBE) }

    private val runner = ProcessRunner { command, _ ->
        when {
            command.last() == "-version" -> result("ffmpeg version 7.1")
            command.first() == "ffprobe" -> probe()
            command.contains("image2pipe") -> ProcessResult(0, png(), "")
            else -> {
                PngCodec.write(FRAME, Path.of(command.last()).resolveSibling(RasterSequenceCacheCodec.fileName(0)))
                result("")
            }
        }
    }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-trim")
        video = tempRoot.resolve("walk.mov").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val game = tempRoot.resolve("game").createDirectories()
        game.resolve("roto.json").writeText(ROTO_JSON)
        val library = tempRoot.resolve("library")
        repository = ProfileRepository(library, "demo")
        home = HomeModel(SettingsStore(tempRoot.resolve("settings.json")), runner).apply {
            openProject(game)
            chooseLibrary(library)
            createProfile("hero")
        }
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `importing a video starts on all of it, filed under the first action and facing`() {
        val trim = opened()

        assertEquals(TimeRange(0, 10_000), trim.state.range)
        assertEquals("idle", trim.state.action)
        assertEquals("EAST", trim.state.facing)
        assertEquals(300, trim.frameCount)
    }

    @Test
    fun `a file that is no video stays out, with the reason`() {
        probe = { ProcessResult(1, ByteArray(0), "Invalid data found when processing input") }

        runBlocking { home.importVideo(video) }

        assertNull(trimming())
        assertContains(home.state.message!!, "Invalid data")
    }

    @Test
    fun `a press near a handle drags that handle, and the preview follows it`() {
        val trim = opened()

        trim.press(50, reach = 100)
        trim.drag(2_000)
        assertEquals(TimeRange(2_000, 10_000), trim.state.range)
        assertEquals(2_000, trim.state.playheadMs)

        trim.press(9_950, reach = 100)
        trim.drag(8_000)
        assertEquals(TimeRange(2_000, 8_000), trim.state.range)
        assertEquals(7_966, trim.state.playheadMs)
    }

    @Test
    fun `a press away from the handles scrubs and leaves the range alone`() {
        val trim = opened()

        trim.press(5_010, reach = 100)
        trim.drag(6_010)

        assertEquals(TimeRange(0, 10_000), trim.state.range)
        assertEquals(6_000, trim.state.playheadMs)
    }

    @Test
    fun `the filmstrip and preview load, and a failure is shown rather than thrown`() {
        val trim = opened()

        runBlocking {
            trim.loadStrip()
            trim.loadPreview()
        }

        assertEquals(12, trim.state.strip?.size)
        assertEquals(FRAME, trim.state.preview)
    }

    @Test
    fun `saving files the clip where it was chosen, and home lists it`() {
        val trim = opened()
        trim.chooseAction("walk")
        trim.chooseFacing("WEST")
        trim.press(50, reach = 100)
        trim.drag(1_000)

        assertTrue(runBlocking { trim.save() })
        home.closeClip()

        val clip = home.state.selectedEntry?.profile?.clip("walk", "WEST")
        assertEquals(Clip("walk", "WEST", "walk.mov", TimeRange(1_000, 10_000)), clip)
        assertTrue(repository.folder("hero").sourcesFolder.resolve("walk.mov").exists())
        assertNull(trimming())
    }

    @Test
    fun `cancelling leaves the profile and its sources untouched`() {
        opened()

        home.closeClip()

        assertTrue(repository.load("hero").clips.isEmpty())
        assertFalse(repository.folder("hero").sourcesFolder.resolve("walk.mov").exists())
    }

    @Test
    fun `a failed save stays on the screen with the reason`() {
        val trim = opened()
        probe = { ProcessResult(1, ByteArray(0), "moov atom not found") }

        assertFalse(runBlocking { trim.save() })

        assertContains(trim.state.message!!, "moov atom")
        assertFalse(trim.state.saving)
    }

    @Test
    fun `re-trimming keeps the clip's place and warns before it loses work`() {
        val clip = marked()
        val folder = repository.folder("hero")
        folder.sourcesFolder.resolve(clip.source).writeBytes(byteArrayOf(1, 2, 3))
        repository.save("hero", Profile(ramps = listOf("skin"), clips = listOf(clip)))
        home.refresh()

        runBlocking { home.retrim(clip) }
        val trim = assertNotNull(trimming())

        assertEquals(clip.trim, trim.state.range)
        assertNull(trim.warning)
        trim.chooseAction("idle")
        assertEquals("walk", trim.state.action)
        trim.press(1_000, reach = 100)
        trim.drag(1_500)
        assertContains(trim.warning!!, "clears this clip's key-frame markers")
    }

    @Test
    fun `importing over a filmed clip warns before it replaces it`() {
        repository.save("hero", Profile(ramps = listOf("skin"), clips = listOf(marked())))
        home.refresh()

        val trim = opened()
        assertNull(trim.warning)
        trim.chooseAction("walk")

        assertContains(trim.warning!!, "walk_EAST is already filmed")
        assertContains(trim.warning!!, "markers")
    }

    private fun trimming(): TrimModel? = (home.screen as? Screen.Trimming)?.model

    private fun opened(): TrimModel {
        runBlocking { home.importVideo(video) }
        return assertNotNull(trimming(), home.state.message)
    }

    private fun marked() = Clip(
        "walk",
        "EAST",
        "a.mov",
        TimeRange(1_000, 3_000),
        markers = listOf(0, 10),
        zoning = ZoneSettings(listOf(Zone("body", "skin", listOf(ZoneSample(0, 1, 1))))),
    )

    private fun result(stdout: String) = ProcessResult(0, stdout.toByteArray(Charsets.UTF_8), "")

    private fun png(): ByteArray = ByteArrayOutputStream().also { PngCodec.write(FRAME, it) }.toByteArray()

    private companion object {
        val FRAME: Raster = Raster.of(2, 2) { x, y -> (0xFF shl 24) or (x shl 8) or y }

        const val PROBE = """{ "streams": [{ "width": 2, "height": 2, "avg_frame_rate": "30/1", "duration": "10" }] }"""

        const val ROTO_JSON = """{
  "id": "demo",
  "palette": "palette.gpl",
  "ramps": "ramps.json",
  "facings": ["EAST", "WEST"],
  "actions": ["idle", "walk"],
  "canvas": { "width": 20, "height": 40, "groundRow": 39 },
  "output": "art/{profile}/{action}_{facing:lower}/{frame:000}.png",
  "manifest": "art/{profile}/{action}_{facing:lower}/clip.json"
}
"""
    }
}
