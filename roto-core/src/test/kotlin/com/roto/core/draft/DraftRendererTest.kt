package com.roto.core.draft

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.CanvasSampler
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.IndexedRaster
import com.roto.core.project.Canvas
import com.roto.core.shade.DraftColours
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DraftRendererTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    /**
     * A 7x6 canvas at one canvas pixel per frame pixel: odd width with the root on 3 puts canvas pixel
     * `n` exactly on frame pixel `n`, so the frame maps straight across.
     */
    private val boxes =
        FrameBoxes(Canvas(7, 6, groundRow = 4), scaleX = 1.0, scaleY = 1.0, groundY = 4, centreX = List(3) { 3 })
    private val zoneMaps = List(3) { IndexedRaster.of(6, 6) { x, y -> if (x in 1..4 && y in 1..4) 1 else 0 } }

    /** Shaded drafts for key frames 0 and 2, sampled the same way: zone 1 all its middle colour. */
    private val shaded = listOf(0, 2).map { frame ->
        CanvasSampler.sample(IndexedRaster.of(6, 6) { x, y -> zoneMaps[frame][x, y] * 3 }, boxes, frame, 4)
    }
    private val colours = DraftColours(listOf(listOf(2, 3, 4)))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-draft")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `each key frame is finished at canvas size, then read back from the cache`() {
        val drafts = render().get()
        loads = 0

        assertEquals(drafts, render().get())
        assertEquals(0, loads)
        assertEquals(2, drafts.size)
        val expected = grid(".......", ".2222..", ".2332..", ".2332..", ".2222..", ".......")
        assertEquals(expected.drawing(), drafts.first().drawing())
    }

    @Test
    fun `colours, settings and key frames change the key`() {
        val key = render().key

        assertEquals(DraftRenderer.STAGE, key.stage)
        assertNotEquals(key, render(colours = colours.copy(outline = 9)).key)
        assertNotEquals(key, render(settings = DraftSettings(dither = true)).key)
        assertNotEquals(key, render(keyFrames = KeyFrames(listOf(0, 1, 3))).key)
    }

    private fun render(
        keyFrames: KeyFrames = KeyFrames(listOf(0, 2, 3)),
        colours: DraftColours = this.colours,
        settings: DraftSettings = DraftSettings(),
    ) = DraftRenderer(StageCache(tempRoot.resolve("cache"))).render(
        DraftRenderer.Inputs(
            shaded = StageOutput(CacheKey.of("draft-shade", 1) {}) { shaded.also { loads++ } },
            zones = StageOutput(CacheKey.of("zone", 1) {}) { zoneMaps.also { loads++ } },
            boxes = StageOutput(CacheKey.of("framebox", 1) {}) { boxes.also { loads++ } },
        ),
        keyFrames,
        colours,
        settings,
    )
}
