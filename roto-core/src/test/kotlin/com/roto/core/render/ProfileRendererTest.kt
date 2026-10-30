package com.roto.core.render

import com.roto.core.ase.AseCodec
import com.roto.core.cache.StageCache
import com.roto.core.draft.DraftSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Profile
import com.roto.core.render.SyntheticWalk.Companion.CLIP
import com.roto.core.render.SyntheticWalk.Companion.PROFILE
import com.roto.core.video.FrameRate
import com.roto.core.zone.ZoneSettings
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `masks need neither a scale nor zones, and come at the probed frame rate`() {
        val drawing = Drawing(walk.project.config.canvas, ProjectPalette.load(walk.project), scale = null)
        val clip = CLIP.copy(zoning = ZoneSettings())
        val pipeline = ClipPipeline(
            walk.ffmpeg,
            StageCache(walk.folder.cacheFolder),
            drawing,
            clip,
            walk.folder.sourceFile(clip),
        )

        val masks = pipeline.masks.get()

        assertEquals(SyntheticWalk.FRAMES, masks.size)
        assertTrue(masks.all { it.count() > 0 })
        assertEquals(FrameRate(30, 1), pipeline.frameRate)
    }

    private fun renderer() = ProfileRenderer(walk.project, walk.ffmpeg)

    private fun artCels() = AseCodec.read(walk.folder.handoffFile(CLIP)).let { sprite ->
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        sprite.frames.map { frame -> frame.cels.filter { it.layerIndex == art } }
    }
}
