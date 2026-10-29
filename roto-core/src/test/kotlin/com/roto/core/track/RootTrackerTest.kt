package com.roto.core.track

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
import kotlin.test.assertNotEquals

class RootTrackerTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    private val walk = (0 until 5).map { frame ->
        Mask.of(40, 20) { x, y -> x in 2 + 4 * frame..6 + 4 * frame && y in 2..17 }
    }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-root")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the track is computed once and read back as it was`() {
        val first = tracker().track(masks(), RootSettings()).get()
        loads = 0

        assertEquals(first, tracker().track(masks(), RootSettings()).get())
        assertEquals(Stabiliser.track(walk, RootSettings()), first)
        assertEquals(0, loads)
    }

    @Test
    fun `masks and settings each change the key`() {
        val key = tracker().track(masks(), RootSettings()).key

        assertEquals(RootTracker.STAGE, key.stage)
        assertNotEquals(key, tracker().track(masks("other"), RootSettings()).key)
        assertNotEquals(key, tracker().track(masks(), RootSettings(smoothing = 3)).key)
        assertNotEquals(key, tracker().track(masks(), RootSettings(stabilise = false)).key)
    }

    @Test
    fun `a track keeps its lists in step`() {
        assertFailsWith<IllegalArgumentException> { RootTrack(listOf(1.0), listOf(1.0, 2.0), 1, listOf(0)) }
        assertFailsWith<IllegalArgumentException> { RootTrack(emptyList(), emptyList(), 0, emptyList()) }
    }

    private fun tracker() = RootTracker(StageCache(tempRoot.resolve("cache")))

    private fun masks(id: String = "walk"): StageOutput<List<Mask>> =
        StageOutput(CacheKey.of("mask", 1) { put("id", id) }) {
            loads++
            walk
        }
}
