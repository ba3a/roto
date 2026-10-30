package com.roto.app

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.mask.BrushMode
import com.roto.core.mask.MaskSettings
import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.settings.SettingsStore
import com.roto.core.video.Ffmpeg
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
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

class ClipSetupModelTest {

    private lateinit var tempRoot: Path
    private lateinit var repository: ProfileRepository
    private lateinit var home: HomeModel
    private lateinit var setup: ClipSetupModel
    private var probe = { result(PROBE) }

    private val runner = ProcessRunner { command, _ ->
        when {
            command.last() == "-version" -> result("ffmpeg version 7.1")
            command.first() == "ffprobe" -> probe()
            else -> {
                val entry = Path.of(command.last()).parent
                repeat(FRAMES) { PngCodec.write(frame(it), entry.resolve(RasterSequenceCacheCodec.fileName(it))) }
                result("")
            }
        }
    }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-setup")
        val game = tempRoot.resolve("game").createDirectories()
        game.resolve("roto.json").writeText(ROTO_JSON)
        val library = tempRoot.resolve("library")
        repository = ProfileRepository(library, "demo")
        home = HomeModel(SettingsStore(tempRoot.resolve("settings.json")), Ffmpeg(runner)).apply {
            openProject(game)
            chooseLibrary(library)
            createProfile("hero")
        }
        repository.folder("hero").sourcesFolder.resolve(CLIP.source).writeBytes(byteArrayOf(1, 2, 3))
        repository.save("hero", Profile(clips = listOf(CLIP)))
        home.refresh()
        home.setUp(CLIP)
        setup = assertNotNull((home.screen as? Screen.SettingUp)?.model)
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `opening reads the frames, and the preview masks the figure against the plate`() {
        loaded()

        val state = setup.state
        assertEquals(FRAMES, state.frameCount)
        assertEquals(frame(0), state.picture)
        val mask = assertNotNull(state.mask)
        assertTrue(mask[LEFT + 3, 20])
        assertFalse(mask[70, 20])
        assertEquals(Argb.of(70, 75, 80), assertNotNull(state.plate)[LEFT + 3, 20])
    }

    @Test
    fun `a missing or unreadable video stays on the screen as a message`() {
        probe = { ProcessResult(1, ByteArray(0), "moov atom not found") }

        runBlocking { setup.open() }

        assertContains(setup.state.message!!, "moov atom")
        assertEquals(0, setup.state.frameCount)
    }

    @Test
    fun `changing the mask settings changes the preview`() {
        loaded()
        val before = setup.state.mask

        setup.changeMask { it.withLow(0.6).withHigh(0.9) }
        runBlocking { setup.refresh() }

        assertEquals(0, assertNotNull(setup.state.mask).count())
        assertTrue(assertNotNull(before).count() > 0)
    }

    @Test
    fun `an empty wall range starts at the top of the video and its handles obey the trim rules`() {
        loaded()

        setup.useEmptyRange()
        assertEquals(PlateSource.EmptyRange(TimeRange(0, 1_000)), setup.state.clip.plate)
        setup.moveEmptyStart(500)
        setup.moveEmptyEnd(100)
        assertEquals(PlateSource.EmptyRange(TimeRange(500, 533)), setup.state.clip.plate)

        setup.useClipMedian()
        assertEquals(PlateSource.ClipMedian, setup.state.clip.plate)
    }

    @Test
    fun `key frames come from the stages, and the brush only works on them`() {
        loaded()
        setup.selectFrame(2)
        setup.beginStroke(5, 5)
        assertNull(setup.state.stroke)

        runBlocking { setup.loadKeyFrames() }

        assertEquals(listOf(0, 2, 3, 5, 6, 8, 9, 11), setup.state.keyFrames)
        assertTrue(setup.state.canBrush)
        setup.selectFrame(1)
        assertFalse(setup.state.canBrush)
        setup.beginStroke(5, 5)
        assertNull(setup.state.stroke)
    }

    @Test
    fun `a stroke shows on the mask as it is painted and joins the clip when it ends`() {
        keyFrameOpen()
        val before = assertNotNull(setup.state.mask).count()

        setup.changeBrush(Brush(BrushMode.ADD, radius = 2))
        setup.beginStroke(70, 10)
        setup.extendStroke(70, 20)

        assertTrue(assertNotNull(setup.state.mask)[70, 15])
        assertTrue(assertNotNull(setup.state.mask).count() > before)
        assertTrue(setup.state.clip.fixes.isEmpty())

        setup.endStroke()

        assertNull(setup.state.stroke)
        val stroke = setup.state.clip.fixes.single()
        assertEquals(0, stroke.frame)
        assertEquals(2, stroke.radius)
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun `a stroke needs the mask showing, and running off the picture is cut short`() {
        keyFrameOpen()

        setup.selectView(SetupView.FRAME)
        setup.beginStroke(5, 5)
        assertNull(setup.state.stroke)

        setup.selectView(SetupView.MASK)
        setup.beginStroke(5, 5)
        setup.extendStroke(-40, -40)
        setup.endStroke()
        assertEquals(0, setup.state.clip.fixes.single().points.last().x)
    }

    @Test
    fun `undo and clear only touch the frame being looked at`() {
        keyFrameOpen()
        paint(10)
        paint(20)
        setup.selectFrame(2)
        paint(30)

        setup.undoStroke()
        assertEquals(listOf(0, 0), setup.state.clip.fixes.map { it.frame })

        setup.selectFrame(0)
        setup.undoStroke()
        assertEquals(10, setup.state.clip.fixes.single().points.single().x)

        setup.clearFrame()
        assertTrue(setup.state.clip.fixes.isEmpty())
    }

    @Test
    fun `the mask a fix sits on survives new settings`() {
        keyFrameOpen()
        setup.changeBrush(Brush(BrushMode.ADD, radius = 3))
        setup.beginStroke(70, 10)
        setup.endStroke()

        setup.changeMask { it.copy(openRadius = 0) }
        runBlocking { setup.refresh() }

        assertTrue(assertNotNull(setup.state.mask)[70, 10])
    }

    @Test
    fun `saving writes the clip into the profile and home lists it`() {
        keyFrameOpen()
        setup.changeMask { it.copy(closeRadius = 3) }
        paint(10)

        runBlocking { setup.save() }
        home.closeClip()

        val clip = assertNotNull(repository.load("hero").clip("walk", "EAST"))
        assertEquals(CLIP.mask.copy(closeRadius = 3), clip.mask)
        assertEquals(1, clip.fixes.size)
        assertEquals(clip, home.state.selectedEntry?.profile?.clip("walk", "EAST"))
        assertEquals(Screen.Home, home.screen)
    }

    @Test
    fun `a picture fits its box, centred, and a press finds its pixel`() {
        val fit = Fit.of(boxWidth = 400, boxHeight = 400, pictureWidth = 80, pictureHeight = 40)

        assertEquals(Fit(5f, 0f, 100f, 400, 200), fit)
        assertEquals(10, fit.pictureX(52f))
        assertEquals(1, fit.pictureY(108f))
    }

    private fun paint(x: Int) {
        setup.beginStroke(x, 5)
        setup.endStroke()
    }

    private fun loaded() = runBlocking {
        setup.open()
        setup.refresh()
    }

    private fun keyFrameOpen() {
        loaded()
        runBlocking { setup.loadKeyFrames() }
    }

    private fun result(stdout: String) = ProcessResult(0, stdout.toByteArray(Charsets.UTF_8), "")

    private companion object {
        const val FRAMES = 12
        const val LEFT = 10

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

        /** Frame [index]: an orange block moving 4 px a frame across a dark wall. */
        fun frame(index: Int): Raster {
            val left = LEFT + 4 * index
            return Raster.of(80, 50) { x, y ->
                if (x in left until left + 8 && y in 10..39) Argb.of(230, 160, 120) else Argb.of(70, 75, 80)
            }
        }

        const val ROTO_JSON = """{
  "id": "demo",
  "palette": "palette.gpl",
  "ramps": "ramps.json",
  "facings": ["EAST", "WEST"],
  "actions": ["walk"],
  "canvas": { "width": 20, "height": 40, "groundRow": 39 },
  "output": "art/{profile}/{action}_{facing:lower}/{frame:000}.png",
  "manifest": "art/{profile}/{action}_{facing:lower}/clip.json"
}
"""
    }
}
