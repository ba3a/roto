package com.roto.core.cycle

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import com.roto.core.track.RootSettings
import com.roto.core.track.RootTrack
import com.roto.core.track.Stabiliser
import com.roto.core.video.FrameRate
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CycleAnalyserTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    private val walk = (0 until 30).map { walker(it) }
    private val rate = FrameRate(25, 1)

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-cycle")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the analysis is computed once and read back as it was`() {
        val first = analyser().analyse(masks(), track(), rate, CycleSettings(), loop = true).get()
        loads = 0

        assertEquals(first, analyser().analyse(masks(), track(), rate, CycleSettings(), loop = true).get())
        assertEquals(10, first.repeatFrames)
        assertEquals(0, loads)
    }

    @Test
    fun `every input changes the key`() {
        val key = analyser().analyse(masks(), track(), rate, CycleSettings(), loop = true).key

        assertEquals(CycleAnalyser.STAGE, key.stage)
        assertNotEquals(key, analyser().analyse(masks("other"), track(), rate, CycleSettings(), loop = true).key)
        assertNotEquals(key, analyser().analyse(masks(), track("other"), rate, CycleSettings(), loop = true).key)
        assertNotEquals(key, analyser().analyse(masks(), track(), FrameRate(30, 1), CycleSettings(), loop = true).key)
        assertNotEquals(key, analyser().analyse(masks(), track(), rate, CycleSettings(keyFrames = 6), loop = true).key)
        assertNotEquals(key, analyser().analyse(masks(), track(), rate, CycleSettings(), loop = false).key)
    }

    private fun analyser() = CycleAnalyser(StageCache(tempRoot.resolve("cache")))

    private fun masks(id: String = "walk"): StageOutput<List<Mask>> =
        StageOutput(CacheKey.of("mask", 1) { put("id", id) }) {
            loads++
            walk
        }

    private fun track(id: String = "walk"): StageOutput<RootTrack> =
        StageOutput(CacheKey.of("root", 1) { put("id", id) }) {
            loads++
            Stabiliser.track(walk, RootSettings())
        }
}
