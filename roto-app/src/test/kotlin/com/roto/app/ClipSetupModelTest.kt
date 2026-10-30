package com.roto.app

import com.roto.app.ClipBench.Companion.CLIP
import com.roto.app.ClipBench.Companion.FRAMES
import com.roto.app.ClipBench.Companion.LEFT
import com.roto.app.ClipBench.Companion.PROPOSAL
import com.roto.app.ClipBench.Companion.frame
import com.roto.core.image.Argb
import com.roto.core.mask.BrushMode
import com.roto.core.mask.MaskSettings
import com.roto.core.process.ProcessResult
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.Profile
import com.roto.core.profile.TimeRange
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
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
    private lateinit var bench: ClipBench
    private lateinit var setup: ClipSetupModel
    private val home get() = bench.home
    private val repository get() = bench.repository

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-setup")
        bench = ClipBench(tempRoot)
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
        bench.probe = { ProcessResult(1, ByteArray(0), "moov atom not found") }

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
        setup.painting.press(5, 5)
        assertNull(setup.state.stroke)

        runBlocking { setup.loadProposal() }

        assertEquals(listOf(0, 2, 3, 5, 6, 8, 9, 11), setup.state.keyFrames?.frames)
        assertTrue(setup.state.canBrush)
        setup.selectFrame(1)
        assertFalse(setup.state.canBrush)
        setup.painting.press(5, 5)
        assertNull(setup.state.stroke)
    }

    @Test
    fun `a stroke shows on the mask as it is painted and joins the clip when it ends`() {
        keyFrameOpen()
        val before = assertNotNull(setup.state.mask).count()

        setup.changeBrush(Brush(BrushMode.ADD, radius = 2))
        setup.painting.press(70, 10)
        setup.painting.extend(70, 20)

        assertTrue(assertNotNull(setup.state.mask)[70, 15])
        assertTrue(assertNotNull(setup.state.mask).count() > before)
        assertTrue(setup.state.clip.fixes.isEmpty())

        setup.painting.release()

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
        setup.painting.press(5, 5)
        assertNull(setup.state.stroke)

        setup.selectView(SetupView.MASK)
        setup.painting.press(5, 5)
        setup.painting.extend(-40, -40)
        setup.painting.release()
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
        setup.painting.press(70, 10)
        setup.painting.release()

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
    fun `the strip samples the clip, and the proposal stands until a marker is touched`() {
        keyFrameOpen()

        assertEquals(FRAMES, setup.state.strip?.size)
        assertEquals(PROPOSAL, setup.state.keyFrames?.boundaries)
        assertNull(setup.state.clip.markers)
    }

    @Test
    fun `a press near a marker selects it and shows its frame without moving it`() {
        keyFrameOpen()

        setup.markers.press(4, reach = 1)

        assertEquals(2, setup.state.marker)
        assertEquals(3, setup.state.frame)
        assertNull(setup.state.clip.markers)
    }

    @Test
    fun `dragging a marker turns the proposal into the artist's markers, and the preview follows`() {
        keyFrameOpen()

        setup.markers.press(5, reach = 0)
        setup.markers.drag(4)

        assertEquals(listOf(0, 2, 3, 4, 6, 8, 9, 11, 12), setup.state.clip.markers)
        assertEquals(4, setup.state.frame)
        assertTrue(setup.state.canBrush)
    }

    @Test
    fun `a marker stops at its neighbour`() {
        keyFrameOpen()

        setup.markers.press(5, reach = 0)
        setup.markers.drag(99)

        assertEquals(5, setup.state.clip.markers?.get(3))
        assertEquals(PROPOSAL.size, setup.state.clip.markers?.size)
    }

    @Test
    fun `the end marker can be taken to the clip's end, showing the last frame`() {
        repository.save("hero", Profile(clips = listOf(CLIP.copy(markers = listOf(0, 4, 8)))))
        home.refresh()
        home.setUp(assertNotNull(repository.load("hero").clip("walk", "EAST")))
        setup = assertNotNull((home.screen as? Screen.SettingUp)?.model)
        keyFrameOpen()

        setup.markers.press(8, reach = 0)
        setup.markers.drag(FRAMES.toLong())

        assertEquals(listOf(0, 4, FRAMES), setup.state.clip.markers)
        assertEquals(FRAMES - 1, setup.state.frame)
    }

    @Test
    fun `a press away from every marker moves the playhead and selects nothing`() {
        keyFrameOpen()
        setup.markers.press(5, reach = 0)

        setup.markers.press(7, reach = 0)
        setup.markers.drag(10)

        assertNull(setup.state.marker)
        assertEquals(10, setup.state.frame)
        assertNull(setup.state.clip.markers)
    }

    @Test
    fun `before the proposal arrives the strip still moves the playhead`() {
        loaded()

        setup.markers.press(7, reach = 2)
        setup.markers.drag(9)

        assertEquals(9, setup.state.frame)
        setup.markers.add()
        assertNull(setup.state.clip.markers)
    }

    @Test
    fun `a marker added at the playhead is selected, and can be removed again`() {
        keyFrameOpen()
        setup.selectFrame(4)

        setup.markers.add()
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 8, 9, 11, 12), setup.state.clip.markers)
        assertEquals(3, setup.state.marker)
        assertTrue(setup.state.canBrush)

        setup.markers.remove()
        assertEquals(PROPOSAL, setup.state.clip.markers)
        assertNull(setup.state.marker)
    }

    @Test
    fun `the last key frame cannot be removed`() {
        repository.save("hero", Profile(clips = listOf(CLIP.copy(markers = listOf(0, 4)))))
        home.refresh()
        home.setUp(assertNotNull(repository.load("hero").clip("walk", "EAST")))
        setup = assertNotNull((home.screen as? Screen.SettingUp)?.model)
        keyFrameOpen()

        setup.markers.press(4, reach = 0)
        setup.markers.remove()

        assertEquals(listOf(0, 4), setup.state.clip.markers)
    }

    @Test
    fun `going back to the proposal drops the artist's markers`() {
        keyFrameOpen()
        setup.markers.press(5, reach = 0)
        setup.markers.drag(4)

        setup.markers.useProposal()

        assertNull(setup.state.clip.markers)
        assertEquals(PROPOSAL, setup.state.keyFrames?.boundaries)
    }

    @Test
    fun `a stroke left behind by a marker is counted, and counts again when it returns`() {
        keyFrameOpen()
        setup.selectFrame(5)
        paint(10)
        assertEquals(0, setup.state.strandedFixes)

        setup.markers.press(5, reach = 0)
        setup.markers.drag(4)
        assertEquals(1, setup.state.strandedFixes)

        setup.markers.drag(5)
        assertEquals(0, setup.state.strandedFixes)
    }

    @Test
    fun `saved markers come back, and a new proposal does not replace them`() {
        keyFrameOpen()
        setup.markers.press(5, reach = 0)
        setup.markers.drag(4)
        val markers = setup.state.clip.markers
        runBlocking { setup.save() }

        home.closeClip()
        home.setUp(assertNotNull(repository.load("hero").clip("walk", "EAST")))
        setup = assertNotNull((home.screen as? Screen.SettingUp)?.model)
        keyFrameOpen()

        assertEquals(markers, setup.state.keyFrames?.boundaries)
        assertEquals(PROPOSAL, setup.state.proposal)
    }

    @Test
    fun `a picture fits its box, centred, and a press finds its pixel`() {
        val fit = Fit.of(boxWidth = 400, boxHeight = 400, pictureWidth = 80, pictureHeight = 40)

        assertEquals(Fit(5f, 0f, 100f, 400, 200), fit)
        assertEquals(10, fit.pictureX(52f))
        assertEquals(1, fit.pictureY(108f))
    }

    private fun paint(x: Int) {
        setup.painting.press(x, 5)
        setup.painting.release()
    }

    private fun loaded() = runBlocking {
        setup.open()
        setup.refresh()
    }

    private fun keyFrameOpen() {
        loaded()
        runBlocking { setup.loadProposal() }
    }

}
