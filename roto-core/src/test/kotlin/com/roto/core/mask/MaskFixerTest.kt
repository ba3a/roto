package com.roto.core.mask

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaskFixerTest {

    private lateinit var tempRoot: Path
    private lateinit var fixer: MaskFixer
    private val blank = Mask.of(8, 8) { _, _ -> false }
    private val solid = Mask.of(8, 8) { _, _ -> true }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-mask-fix")
        fixer = MaskFixer(StageCache(tempRoot))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a brush adds a disc and erases a disc`() {
        val added = MaskBrush.apply(blank, listOf(stroke(BrushMode.ADD, radius = 2, BrushPoint(4, 4))))
        val erased = MaskBrush.apply(solid, listOf(stroke(BrushMode.ERASE, radius = 2, BrushPoint(4, 4))))

        assertEquals(13, added.count())
        assertTrue(added[4, 2] && added[6, 4] && added[5, 5])
        assertFalse(added[6, 6])
        assertEquals(64 - 13, erased.count())
    }

    @Test
    fun `a stroke leaves no gap between points, and stops at the edge`() {
        val across = stroke(BrushMode.ADD, radius = 1, BrushPoint(0, 3), BrushPoint(30, 3))

        val line = MaskBrush.apply(blank, listOf(across))

        (0 until 8).forEach { x -> assertTrue(line[x, 3], "column $x") }
    }

    @Test
    fun `strokes apply in order and the mask given stays as it was`() {
        val add = stroke(BrushMode.ADD, radius = 2, BrushPoint(4, 4))
        val erase = stroke(BrushMode.ERASE, radius = 1, BrushPoint(4, 4))

        assertFalse(MaskBrush.apply(blank, listOf(add, erase))[4, 4])
        assertTrue(MaskBrush.apply(blank, listOf(erase, add))[4, 4])
        assertEquals(0, blank.count())
    }

    @Test
    fun `no strokes is the masks themselves, so an unfixed clip keeps its keys`() {
        val masks = masks()

        assertSame(masks, fixer.fixed(masks, emptyList()))
    }

    @Test
    fun `strokes change only their own frame, and are cached`() {
        val fix = stroke(BrushMode.ADD, radius = 1, BrushPoint(2, 2)).copy(frame = 1)

        val fixed = fixer.fixed(masks(), listOf(fix))

        assertEquals(blank, fixed.get()[0])
        assertEquals(5, fixed.get()[1].count())
        assertEquals(fixed.get(), MaskFixer(StageCache(tempRoot)).fixed(masks { error("not read") }, listOf(fix)).get())
    }

    @Test
    fun `the key follows the masks and the strokes`() {
        val fix = stroke(BrushMode.ADD, radius = 1, BrushPoint(2, 2))
        val key = fixer.fixed(masks(), listOf(fix)).key

        assertEquals(MaskFixer.STAGE, key.stage)
        assertEquals(key, fixer.fixed(masks(), listOf(fix)).key)
        assertNotEquals(key, fixer.fixed(masks(), listOf(fix.copy(radius = 2))).key)
        assertNotEquals(key, fixer.fixed(masks("other"), listOf(fix)).key)
    }

    @Test
    fun `a stroke on a frame the clip does not have is refused`() {
        val fix = stroke(BrushMode.ADD, radius = 1, BrushPoint(2, 2)).copy(frame = 7)

        assertFailsWith<IllegalArgumentException> { fixer.fixed(masks(), listOf(fix)).get() }
    }

    @Test
    fun `a stroke must make sense`() {
        assertFailsWith<IllegalArgumentException> { BrushStroke(-1, BrushMode.ADD, 1, listOf(BrushPoint(0, 0))) }
        assertFailsWith<IllegalArgumentException> { BrushStroke(0, BrushMode.ADD, 0, listOf(BrushPoint(0, 0))) }
        assertFailsWith<IllegalArgumentException> { BrushStroke(0, BrushMode.ADD, 1, emptyList()) }
        assertFailsWith<IllegalArgumentException> { BrushPoint(-1, 0) }
    }

    @Test
    fun `moving one threshold past the other brings the other along`() {
        assertEquals(MaskSettings(low = 0.2, high = 0.2), MaskSettings(low = 0.05, high = 0.1).withLow(0.2))
        assertEquals(MaskSettings(low = 0.01, high = 0.01), MaskSettings(low = 0.05, high = 0.1).withHigh(0.01))
        assertEquals(MaskSettings(low = 0.07, high = 0.1), MaskSettings(low = 0.05, high = 0.1).withLow(0.07))
    }

    private fun masks(input: String = "input", produce: () -> List<Mask> = { listOf(blank, blank) }) =
        StageOutput(CacheKey.of("input", 1) { put("name", input) }, produce)

    private fun stroke(mode: BrushMode, radius: Int, vararg points: BrushPoint) =
        BrushStroke(0, mode, radius, points.toList())
}
