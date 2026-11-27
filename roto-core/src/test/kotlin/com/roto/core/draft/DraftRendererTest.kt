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
        FrameBoxes(Canvas(7, 6, groundRow = 4), List(3) { 1.0 }, List(3) { 1.0 }, List(3) { 4 }, List(3) { 3 })
    private val figureMap = IndexedRaster.of(6, 6) { x, y -> if (x in 1..4 && y in 1..4) 1 else 0 }
    private val emptyMap = IndexedRaster.of(6, 6) { _, _ -> 0 }
    private val zoneMaps = List(3) { figureMap }

    /** Shaded drafts for key frames 0 and 2 of [maps], sampled the same way: zone 1 all its middle colour. */
    private fun shadedOf(maps: List<IndexedRaster>) = listOf(0, 2).map { frame ->
        CanvasSampler.sample(IndexedRaster.of(6, 6) { x, y -> maps[frame][x, y] * 3 }, boxes, frame, 4)
    }
    private val shaded = shadedOf(zoneMaps)
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

    @Test
    fun `a hold keeps a block's previous state, and the first key frame ignores the last`() {
        val maps = listOf(emptyMap, emptyMap, figureMap)
        val plain = render(maps = maps).get()
        val held = render(settings = DraftSettings(hold = 8), maps = maps).get()

        val figure = grid(".......", ".2222..", ".2332..", ".2332..", ".2222..", ".......")
        assertEquals(figure.drawing(), plain[1].drawing())
        assertEquals(List(6) { "......." }.joinToString("\n"), held[1].drawing())
        assertEquals(plain[0], held[0])

        val reversed = listOf(figureMap, emptyMap, emptyMap)
        val heldReversed = render(settings = DraftSettings(hold = 8), maps = reversed).get()
        assertEquals(render(maps = reversed).get().first(), heldReversed.first())
    }

    @Test
    fun `two renders from fresh caches are identical, and the hold changes the key`() {
        val settings = DraftSettings(hold = 3)

        val first = render(settings = settings, cache = "a").get()
        assertEquals(first, render(settings = settings, cache = "b").get())
        assertNotEquals(render().key, render(settings = settings).key)
    }

    private fun render(
        keyFrames: KeyFrames = KeyFrames(listOf(0, 2, 3)),
        colours: DraftColours = this.colours,
        settings: DraftSettings = DraftSettings(),
        maps: List<IndexedRaster> = zoneMaps,
        cache: String = "cache",
    ) = DraftRenderer(StageCache(tempRoot.resolve(cache))).render(
        DraftRenderer.Inputs(
            shaded = StageOutput(CacheKey.of("draft-shade", 1) {}) { shadedOf(maps).also { loads++ } },
            zones = StageOutput(CacheKey.of("zone", 1) {}) { maps.also { loads++ } },
            boxes = StageOutput(CacheKey.of("framebox", 1) {}) { boxes.also { loads++ } },
        ),
        keyFrames,
        colours,
        settings,
    )
}
