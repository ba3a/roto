package com.roto.core.shade

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ShadeStagesTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    /** Five frames: a 4x8 figure, its left half dark and right half light, on a black ground. */
    private val clip = List(5) {
        Raster.of(12, 12) { x, y ->
            when {
                x !in 4..7 || y !in 2..9 -> Argb.of(0, 0, 0)
                x < 6 -> Argb.of(60, 60, 60)
                else -> Argb.of(200, 200, 200)
            }
        }
    }
    private val zoneMaps = List(5) { IndexedRaster.of(12, 12) { x, y -> if (x in 4..7 && y in 2..9) 1 else 0 } }
    private val boxes =
        FrameBoxes(Canvas(6, 6, groundRow = 5), scaleX = 0.5, scaleY = 0.5, groundY = 9, centreX = List(5) { 6 })
    private val colours = listOf(listOf(3, 7))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-shade")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the clip's shading is fitted once and read back`() {
        val first = ShadingAnalyser(cache()).shading(frames(), zones(), listOf(2), ShadeSettings()).get()
        loads = 0

        assertEquals(first, ShadingAnalyser(cache()).shading(frames(), zones(), listOf(2), ShadeSettings()).get())
        assertEquals(0, loads)
        assertNotEquals(
            ShadingAnalyser(cache()).shading(frames(), zones(), listOf(2), ShadeSettings()).key,
            ShadingAnalyser(cache()).shading(frames(), zones(), listOf(3), ShadeSettings()).key,
        )
    }

    @Test
    fun `each key frame is shaded at four times the canvas, dark half and light half`() {
        val drafts = draft(KeyFrames(listOf(0, 2, 4))).get()

        assertEquals(2, drafts.size)
        assertEquals(24 to 24, drafts.first().width to drafts.first().height)
        val labels = (0 until 24).map { drafts.first()[it, 20] }.toSet()
        assertEquals(setOf(0, 3, 7), labels)
    }

    @Test
    fun `key frames and colours change the draft's key, and a cached draft loads nothing`() {
        val key = draft(KeyFrames(listOf(0, 2, 4))).key
        draft(KeyFrames(listOf(0, 2, 4))).get()
        loads = 0

        draft(KeyFrames(listOf(0, 2, 4))).get()

        assertEquals(0, loads)
        assertEquals(DraftShader.STAGE, key.stage)
        assertNotEquals(key, draft(KeyFrames(listOf(0, 3, 4))).key)
        assertNotEquals(key, draft(KeyFrames(listOf(0, 2, 4)), listOf(listOf(3, 8))).key)
    }

    private fun cache() = StageCache(tempRoot.resolve("cache"))

    private fun draft(keyFrames: KeyFrames, zoneColours: List<List<Int>> = colours) =
        DraftShader(cache()).shade(
            DraftShader.Inputs(
                frames(),
                zones(),
                StageOutput(CacheKey.of("framebox", 1) {}) { boxes.also { loads++ } },
                ShadingAnalyser(cache()).shading(frames(), zones(), listOf(2), ShadeSettings()),
            ),
            keyFrames,
            zoneColours,
        )

    private fun frames(): StageOutput<List<Raster>> =
        StageOutput(CacheKey.of("frames", 1) {}) { clip.also { loads++ } }

    private fun zones(): StageOutput<List<IndexedRaster>> =
        StageOutput(CacheKey.of("zone", 1) {}) { zoneMaps.also { loads++ } }
}
