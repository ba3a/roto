package com.roto.core.framebox

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootSettings
import com.roto.core.track.RootTrack
import com.roto.core.track.Stabiliser
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FrameBoxerTest {

    private lateinit var tempRoot: Path
    private var loads = 0

    private val walk = (0 until 4).map { frame ->
        Mask.of(60, 50) { x, y -> x in 5 + 4 * frame..11 + 4 * frame && y in 5..44 }
    }
    private val canvas = Canvas(20, 30, 28)
    private val scale = ScaleSettings(bodyHeight = 20, marks = ScaleMarks("walk_EAST", 0, headTopY = 5, feetY = 44))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-framebox")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the boxes are laid out once and read back as they were`() {
        val first = boxer().boxes(masks(), track(), scale, canvas).get()
        loads = 0

        assertEquals(first, boxer().boxes(masks(), track(), scale, canvas).get())
        assertEquals(FrameBoxLayout.of(walk, Stabiliser.track(walk, RootSettings()), scale, canvas), first)
        assertEquals(0, loads)
    }

    @Test
    fun `every input changes the key`() {
        val key = boxer().boxes(masks(), track(), scale, canvas).key

        assertEquals(FrameBoxer.STAGE, key.stage)
        assertNotEquals(key, boxer().boxes(masks("other"), track(), scale, canvas).key)
        assertNotEquals(key, boxer().boxes(masks(), track("other"), scale, canvas).key)
        assertNotEquals(key, boxer().boxes(masks(), track(), scale.copy(squash = 0.9), canvas).key)
        assertNotEquals(key, boxer().boxes(masks(), track(), scale, canvas.copy(groundRow = 29)).key)
    }

    private fun boxer() = FrameBoxer(StageCache(tempRoot.resolve("cache")))

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
