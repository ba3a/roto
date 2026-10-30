package com.roto.app

import com.roto.app.ClipBench.Companion.CLIP
import com.roto.app.ClipBench.Companion.LEFT
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.profile.Profile
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawModelTest {

    private lateinit var tempRoot: Path
    private lateinit var bench: ClipBench
    private lateinit var draw: DrawModel

    /** On the block in frame 0: it spans x 10..17, y 10..39. */
    private val figureX = LEFT + 3

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-draw")
        bench = ClipBench(tempRoot)
        openDraw()
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `opening reads the clip, the key frames and the ramps, and says what is missing`() {
        assertEquals(ClipBench.FRAMES, draw.state.frameCount)
        assertEquals(ClipBench.PROPOSAL, draw.state.keyFrames?.boundaries)
        assertEquals(listOf("skin", "suit"), draw.state.ramps)
        assertEquals(0, draw.state.frame)
        assertContains(draw.state.missing!!, "head and the feet")
    }

    @Test
    fun `a broken palette stays on the screen as a message`() {
        tempRoot.resolve("game").resolve("ramps.json").toFile().writeText("{")

        openDraw()

        assertContains(draw.state.message!!, "ramps.json")
    }

    @Test
    fun `marking head then feet makes a scale, with a first guess at the height`() {
        draw.useTool(DrawTool.Head)
        draw.picture.press(figureX, 10)
        assertNull(draw.state.scale)

        draw.useTool(DrawTool.Feet)
        draw.picture.press(figureX, 39)

        assertEquals(ScaleSettings(34, ScaleMarks(CLIP.id, 0, 10, 39)), draw.state.scale)
        assertContains(draw.state.missing!!, "Add a zone")
    }

    @Test
    fun `the head has to be above the feet`() {
        marked()

        draw.useTool(DrawTool.Head)
        draw.picture.press(figureX, 39)

        assertContains(draw.state.message!!, "above the feet")
        assertEquals(10, draw.state.head)
        assertEquals(10, draw.state.scale?.marks?.headTopY)
    }

    @Test
    fun `new marks keep the height and squash already tuned`() {
        marked()
        draw.setBodyHeight(30)
        draw.setSquash(0.8)

        draw.useTool(DrawTool.Feet)
        draw.picture.press(figureX, 38)

        assertEquals(ScaleSettings(30, ScaleMarks(CLIP.id, 0, 10, 38), squash = 0.8), draw.state.scale)
    }

    @Test
    fun `a scale marked on another clip stays, and its marks are not shown here`() {
        val scale = ScaleSettings(30, ScaleMarks("idle_EAST", 2, 12, 40))
        bench.save(Profile(scale = scale, clips = listOf(CLIP)))
        openDraw()

        assertEquals(scale, draw.state.scale)
        assertNull(draw.state.head)
        assertNull(draw.state.feet)
    }

    @Test
    fun `a new zone is made by the first click on the figure, and further clicks sample it`() {
        draw.useTool(DrawTool.NewZone("coat", "suit"))
        draw.picture.press(figureX, 20)

        assertEquals(Zone("coat", "suit", listOf(ZoneSample(0, figureX, 20))), draw.state.clip.zoning.zones.single())
        assertEquals(DrawTool.Sample("coat"), draw.state.tool)

        draw.picture.press(figureX + 1, 21)

        assertEquals(2, draw.state.clip.zoning.zones.single().samples.size)
    }

    @Test
    fun `a click off the figure samples nothing`() {
        draw.useTool(DrawTool.NewZone("coat", "suit"))

        draw.picture.press(70, 20)

        assertTrue(draw.state.clip.zoning.zones.isEmpty())
        assertContains(draw.state.message!!, "outside the figure")
        assertEquals(DrawTool.NewZone("coat", "suit"), draw.state.tool)
    }

    @Test
    fun `two zones cannot share a name`() {
        zoned()

        draw.useTool(DrawTool.NewZone("coat", "skin"))
        draw.picture.press(figureX, 30)

        assertEquals(1, draw.state.clip.zoning.zones.size)
        assertContains(draw.state.message!!, "already a zone")
    }

    @Test
    fun `undoing a zone's only sample removes the zone and takes it out of the hand`() {
        zoned()
        draw.useTool(DrawTool.Sample("coat"))
        draw.picture.press(figureX, 22)

        draw.removeSample("coat")
        assertEquals(1, draw.state.clip.zoning.zones.single().samples.size)

        draw.removeSample("coat")
        assertTrue(draw.state.clip.zoning.zones.isEmpty())
        assertNull(draw.state.tool)
    }

    @Test
    fun `a zone stroke is painted on key frames only, and removing the zone takes it too`() {
        zoned()
        draw.selectFrame(1)
        draw.useTool(DrawTool.Paint("coat"))

        draw.picture.press(figureX, 20)
        assertNull(draw.state.stroke)
        assertContains(draw.state.message!!, "key frames only")

        draw.selectFrame(2)
        draw.picture.press(figureX + 8, 20)
        draw.picture.extend(figureX + 8, 25)
        assertEquals(2, draw.state.stroke?.points?.size)
        draw.picture.release()

        assertNull(draw.state.stroke)
        assertEquals(2, draw.state.clip.zoning.fixes.single().frame)

        draw.removeZone("coat")
        assertTrue(draw.state.clip.zoning.fixes.isEmpty())
        assertNull(draw.state.tool)
    }

    @Test
    fun `undo takes back the last stroke on this frame only`() {
        zoned()
        draw.useTool(DrawTool.Paint("coat"))
        draw.setBrushRadius(2)
        draw.selectFrame(2)
        draw.picture.press(19, 20)
        draw.picture.release()
        draw.selectFrame(3)
        draw.picture.press(23, 20)
        draw.picture.release()

        draw.undoStroke()

        assertEquals(listOf(2), draw.state.clip.zoning.fixes.map { it.frame })
    }

    @Test
    fun `the strip is drawn to a key frame it comes near, and stops anywhere else`() {
        draw.strip.press(4, reach = 1)
        assertEquals(3, draw.state.frame)

        draw.strip.press(4, reach = 0)
        assertEquals(4, draw.state.frame)
        assertFalse(draw.state.onKeyFrame)
    }

    @Test
    fun `nothing is drawn until there is a scale, then the boxes, then the drafts`() {
        runBlocking { draw.refreshDraft() }
        assertNull(draw.state.boxes)

        marked()
        runBlocking { draw.refreshDraft() }
        assertNotNull(draw.state.boxes)
        assertNull(draw.state.draft)

        zoned()
        runBlocking { draw.refreshDraft() }
        assertEquals(8, draw.state.drafts.size)
        val draft = assertNotNull(draw.state.draft)
        assertEquals(20, draft.width)
        assertEquals(40, draft.height)
        assertTrue(draft.toArgbArray().any { it != 0 })
        assertFalse(draw.state.drawing)
    }

    @Test
    fun `the draft follows the zones`() {
        marked()
        zoned()
        runBlocking { draw.refreshDraft() }
        val before = draw.state.drafts

        draw.chooseRamp("coat", "skin")
        runBlocking { draw.refreshDraft() }

        assertNotEquals(before, draw.state.drafts)
    }

    @Test
    fun `the draft shown is the one of the key frame on screen, held between key frames`() {
        marked()
        zoned()
        runBlocking { draw.refreshDraft() }

        draw.selectFrame(2)
        val atKeyFrame = draw.state.draft

        assertEquals(draw.state.drafts[1], atKeyFrame)
        draw.selectFrame(4)
        assertFalse(draw.state.onKeyFrame)
        assertEquals(draw.state.drafts[2], draw.state.draft)
    }

    @Test
    fun `saving writes the scale, the zones and every ramp they use`() {
        marked()
        zoned()
        draw.setDither(true)

        runBlocking { draw.save() }

        val profile = bench.repository.load("hero")
        assertEquals(draw.state.scale, profile.scale)
        assertEquals(listOf("suit"), profile.ramps)
        val clip = bench.storedClip()
        assertEquals(draw.state.clip.zoning, clip.zoning)
        assertTrue(clip.draft.dither)
    }

    @Test
    fun `marks come back when the clip is opened again`() {
        marked()
        runBlocking { draw.save() }

        openDraw()

        assertEquals(10, draw.state.head)
        assertEquals(39, draw.state.feet)
    }

    private fun marked() {
        draw.useTool(DrawTool.Head)
        draw.picture.press(figureX, 10)
        draw.useTool(DrawTool.Feet)
        draw.picture.press(figureX, 39)
        draw.useTool(null)
    }

    private fun zoned() {
        draw.useTool(DrawTool.NewZone("coat", "suit"))
        draw.picture.press(figureX, 20)
        draw.useTool(null)
    }

    private fun openDraw() {
        bench.home.closeClip()
        bench.home.draw(bench.storedClip())
        draw = assertNotNull((bench.home.screen as? Screen.Drawing)?.model)
        runBlocking { draw.open() }
    }
}
