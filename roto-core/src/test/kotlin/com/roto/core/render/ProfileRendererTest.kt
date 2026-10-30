package com.roto.core.render

import com.roto.core.ase.AseCodec
import com.roto.core.cache.StageCache
import com.roto.core.cycle.CycleAnalyser
import com.roto.core.draft.DraftSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.mask.BrushMode
import com.roto.core.mask.BrushPoint
import com.roto.core.mask.BrushStroke
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.publish.ClipManifestCodec
import com.roto.core.publish.Publisher
import com.roto.core.render.SyntheticWalk.Companion.CLIP
import com.roto.core.render.SyntheticWalk.Companion.PROFILE
import com.roto.core.render.SyntheticWalk.Companion.STILL
import com.roto.core.video.FrameRate
import com.roto.core.zone.ZoneSettings
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileRendererTest {

    private lateinit var tempRoot: Path
    private lateinit var walk: SyntheticWalk

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-render")
        walk = SyntheticWalk(tempRoot)
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `every clip's handoff is written, one frame per key frame`() {
        val report = renderer().render(PROFILE, walk.folder)

        assertEquals(listOf(walk.folder.handoffFile(CLIP)), report.files)
        val sprite = AseCodec.read(walk.folder.handoffFile(CLIP))
        assertEquals(SyntheticWalk.KEY_FRAMES, sprite.frames.size)
        assertEquals(
            listOf(HandoffMerge.REFERENCE, HandoffMerge.DRAFT, HandoffMerge.ART),
            sprite.layers.map { it.name },
        )
    }

    @Test
    fun `a render from a warm cache extracts no frames again`() {
        renderer().render(PROFILE, walk.folder)
        renderer().render(PROFILE, walk.folder)

        assertEquals(1, walk.extractions())
    }

    @Test
    fun `re-rendering with new draft settings keeps the art`() {
        renderer().render(PROFILE, walk.folder)
        walk.trace(CLIP)
        val art = artCels()

        val undithered = CLIP.copy(draft = DraftSettings(dither = false))
        renderer().render(PROFILE.copy(clips = listOf(undithered)), walk.folder)

        assertEquals(art, artCels())
    }

    @Test
    fun `a profile without clips has nothing to render`() {
        val failure = assertFailsWith<RenderException> { renderer().render(Profile(), walk.folder) }

        assertContains(failure.message!!, "nothing to render")
    }

    @Test
    fun `an unmarked scale stops the render, naming the clip`() {
        val failure = assertFailsWith<RenderException> { renderer().render(PROFILE.copy(scale = null), walk.folder) }

        assertContains(failure.message!!, CLIP.id)
        assertContains(failure.message!!, "scale")
    }

    @Test
    fun `a clip with no zones sampled stops the render, naming the clip`() {
        val unzoned = PROFILE.copy(clips = listOf(CLIP.copy(zoning = ZoneSettings())))

        val failure = assertFailsWith<RenderException> { renderer().render(unzoned, walk.folder) }

        assertContains(failure.message!!, CLIP.id)
        assertContains(failure.message!!, "zones")
    }

    @Test
    fun `masking needs neither a palette nor a scale nor zones, and comes at the probed frame rate`() {
        val clip = CLIP.copy(zoning = ZoneSettings())
        val footage = footage(clip)

        val masks = ClipMasking(footage, cache(), clip).masks.get()

        assertEquals(SyntheticWalk.FRAMES, masks.size)
        assertTrue(masks.all { it.count() > 0 })
        assertEquals(FrameRate(30, 1), footage.frameRate)
    }

    @Test
    fun `every masking of a clip shares one copy of its frames`() {
        val footage = footage(CLIP)
        val frames = footage.frames.get()

        ClipMasking(footage, cache(), CLIP).masks.get()
        ClipMasking(footage, cache(), CLIP.copy(mask = CLIP.mask.copy(openRadius = 0))).masks.get()

        assertSame(frames, footage.frames.get())
        assertEquals(1, walk.extractions())
    }

    @Test
    fun `a brush fix reaches the draft and moves neither the key frames nor the crop`() {
        val plain = pipeline(CLIP.copy(fixes = emptyList()))
        val keyFrame = plain.masking.keyFrames.frames[1]
        // The walker's centre is 20 + 2 * frame: this paints just beside it, inside the canvas crop.
        val beside = BrushPoint(20 + 2 * keyFrame + 12, 30)
        val fixes = listOf(BrushStroke(keyFrame, BrushMode.ADD, radius = 3, points = listOf(beside)))
        val fixed = pipeline(CLIP.copy(fixes = fixes))

        assertEquals(plain.masking.keyFrames, fixed.masking.keyFrames)
        assertEquals(plain.boxes.key, fixed.boxes.key)
        assertNotEquals(plain.drafts.get()[1], fixed.drafts.get()[1])
        assertEquals(plain.drafts.get()[0], fixed.drafts.get()[0])
    }

    @Test
    fun `markers set the handoff's key frames and durations, and the cycle stage is not run`() {
        val marked = CLIP.copy(markers = listOf(2, 5, 9), fixes = emptyList())

        renderer().render(PROFILE.copy(clips = listOf(marked)), walk.folder)

        val sprite = AseCodec.read(walk.folder.handoffFile(marked))
        assertEquals(listOf(100, 134), sprite.frames.map { it.durationMs })
        assertFalse(walk.folder.cacheFolder.resolve(CycleAnalyser.STAGE).exists())
    }

    @Test
    fun `a mask fix beyond the clip stops the render, naming the clip`() {
        val stroke = BrushStroke(99, BrushMode.ADD, radius = 1, points = listOf(BrushPoint(1, 1)))
        val beyond = PROFILE.copy(clips = listOf(CLIP.copy(fixes = listOf(stroke))))

        val failure = assertFailsWith<RenderException> { renderer().render(beyond, walk.folder) }

        assertContains(failure.message!!, CLIP.id)
        assertContains(failure.message!!, "frame 99")
    }

    @Test
    fun `a still goes through the same stages to one key frame shown for a second, and probes nothing`() {
        walk.useStill()
        val profile = walk.repository.load(SyntheticWalk.PROFILE_ID)

        renderer().render(profile, walk.folder)

        val sprite = AseCodec.read(walk.folder.handoffFile(STILL))
        assertEquals(listOf(1_000), sprite.frames.map { it.durationMs })
        assertEquals(
            listOf(HandoffMerge.REFERENCE, HandoffMerge.DRAFT, HandoffMerge.ART),
            sprite.layers.map { it.name },
        )
        assertFalse(walk.runner.commands.any { it.first() == "ffprobe" })
        assertEquals(0, walk.extractions())
    }

    @Test
    fun `a still publishes as a one-frame clip that does not loop`() {
        walk.useStill()
        val profile = walk.repository.load(SyntheticWalk.PROFILE_ID)
        renderer().render(profile, walk.folder)
        walk.trace(STILL)

        Publisher(walk.project, walk.runner).publish(SyntheticWalk.PROFILE_ID, profile, walk.folder)

        val frame = walk.project.frameFile(SyntheticWalk.PROFILE_ID, STILL.action, STILL.facing, 0)
        assertTrue(PngCodec.read(frame).toArgbArray().any { Argb.alpha(it) != 0 })
        val manifestFile = walk.project.manifestFile(SyntheticWalk.PROFILE_ID, STILL.action, STILL.facing)
        val manifest = ClipManifestCodec.read(manifestFile)
        assertEquals(1, manifest.frames.size)
        assertFalse(manifest.loop)
    }

    @Test
    fun `a wall photo with nobody missing from it stops the render, naming the clip`() {
        val noFigure = Clip.still("idle", "EAST", "wall.png", "wall.png")
        val profile = PROFILE.copy(clips = listOf(noFigure.copy(zoning = STILL.zoning)))

        val failure = assertFailsWith<RenderException> { renderer().render(profile, walk.folder) }

        assertContains(failure.message!!, noFigure.id)
        assertContains(failure.message!!, "figure")
    }

    private fun cache() = StageCache(walk.folder.cacheFolder)

    private fun footage(clip: Clip) = ClipFootage(walk.ffmpeg, cache(), walk.folder, clip)

    private fun pipeline(clip: Clip): ClipPipeline {
        val drawing = Drawing(walk.project.config.canvas, ProjectPalette.load(walk.project), PROFILE.scale)
        return ClipPipeline(ClipMasking(footage(clip), cache(), clip), cache(), drawing)
    }

    private fun renderer() = ProfileRenderer(walk.project, walk.ffmpeg)

    private fun artCels() = AseCodec.read(walk.folder.handoffFile(CLIP)).let { sprite ->
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        sprite.frames.map { frame -> frame.cels.filter { it.layerIndex == art } }
    }
}
