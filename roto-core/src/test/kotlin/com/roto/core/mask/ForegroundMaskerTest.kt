package com.roto.core.mask

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ForegroundMaskerTest {

    private lateinit var tempRoot: Path
    private lateinit var masker: ForegroundMasker

    private val wall = Argb.of(200, 195, 185)
    private val shirt = Argb.of(40, 70, 160)
    private val plate = Raster.of(12, 8) { _, _ -> wall }

    /** A 4x4 figure stepping two pixels right each frame. */
    private val walk = (0 until 3).map { step ->
        Raster.of(12, 8) { x, y -> if (x in 2 + step * 2..5 + step * 2 && y in 2..5) shirt else wall }
    }

    private var loads = 0

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-mask")
        masker = ForegroundMasker(StageCache(tempRoot.resolve("cache")))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `one mask per frame, the figure where it stands`() {
        val masks = masker.masks(frames(), plate(), MaskSettings()).get()

        val expected = (0 until 3).map { step ->
            Mask.of(12, 8) { x, y -> x in 2 + step * 2..5 + step * 2 && y in 2..5 }
        }
        assertEquals(expected, masks)
    }

    @Test
    fun `cached masks load neither frames nor plate`() {
        val first = masker.masks(frames(), plate(), MaskSettings()).get()
        loads = 0

        val again = ForegroundMasker(StageCache(tempRoot.resolve("cache"))).masks(frames(), plate(), MaskSettings())
        assertEquals(first, again.get())
        assertEquals(0, loads)
    }

    @Test
    fun `frames, plate and settings each change the key`() {
        val key = masker.masks(frames(), plate(), MaskSettings()).key

        assertEquals(ForegroundMasker.STAGE, key.stage)
        assertEquals(key, masker.masks(frames(), plate(), MaskSettings()).key)
        assertNotEquals(key, masker.masks(frames("other"), plate(), MaskSettings()).key)
        assertNotEquals(key, masker.masks(frames(), plate("other"), MaskSettings()).key)
        assertNotEquals(key, masker.masks(frames(), plate(), MaskSettings(high = 0.2)).key)
    }

    private fun frames(id: String = "walk"): StageOutput<List<Raster>> =
        StageOutput(CacheKey.of("frames", 1) { put("id", id) }) {
            loads++
            walk
        }

    private fun plate(id: String = "wall"): StageOutput<Raster> =
        StageOutput(CacheKey.of("plate", 1) { put("id", id) }) {
            loads++
            plate
        }
}
