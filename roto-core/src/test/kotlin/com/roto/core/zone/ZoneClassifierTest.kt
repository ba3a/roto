package com.roto.core.zone

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

class ZoneClassifierTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    private val skin = Argb.of(160, 103, 75)
    private val suit = Argb.of(52, 80, 138)

    /** A head over a body, stepping right a pixel each frame. */
    private val walk = (0 until 3).map { step ->
        Raster.of(12, 10) { x, y ->
            if (x in 2 + step..5 + step) (if (y < 3) skin else suit) else Argb.of(200, 195, 185)
        }
    }
    private val walkMasks = (0 until 3).map { step -> Mask.of(12, 10) { x, _ -> x in 2 + step..5 + step } }
    private val settings = ZoneSettings(
        listOf(Zone("face", "skin", listOf(ZoneSample(0, 3, 1))), Zone("jacket", "suit", listOf(ZoneSample(0, 3, 6)))),
    )

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-zone")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `every frame is labelled from the one sampled frame, then read back from the cache`() {
        val labels = classifier().zones(frames(), masks(), settings).get()
        loads = 0

        assertEquals(labels, classifier().zones(frames(), masks(), settings).get())
        assertEquals(0, loads)
        assertEquals(1, labels[0][3, 1])
        assertEquals(2, labels[1][4, 6])
        assertEquals(0, labels[2][2, 5])
    }

    @Test
    fun `frames, masks and settings each change the key`() {
        val key = classifier().zones(frames(), masks(), settings).key

        assertEquals(ZoneClassifier.STAGE, key.stage)
        assertNotEquals(key, classifier().zones(frames("other"), masks(), settings).key)
        assertNotEquals(key, classifier().zones(frames(), masks("other"), settings).key)
        assertNotEquals(key, classifier().zones(frames(), masks(), settings.copy(lightnessWeight = 0.3)).key)
    }

    private fun classifier() = ZoneClassifier(StageCache(tempRoot.resolve("cache")))

    private fun frames(id: String = "walk"): StageOutput<List<Raster>> =
        StageOutput(CacheKey.of("frames", 1) { put("id", id) }) {
            loads++
            walk
        }

    private fun masks(id: String = "walk"): StageOutput<List<Mask>> =
        StageOutput(CacheKey.of("mask", 1) { put("id", id) }) {
            loads++
            walkMasks
        }
}
