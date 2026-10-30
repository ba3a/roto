package com.roto.core.cycle

import com.roto.core.video.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyFramesTest {

    @Test
    fun `every boundary but the last is drawn, lasting until the next`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(2, 5), keys.frames)
        assertEquals(listOf(120L, 160L), keys.durationsMs(FrameRate(25, 1)))
    }

    @Test
    fun `durations are exact at a fractional frame rate`() {
        val keys = KeyFrames((0..30 step 3).toList())

        val durations = keys.durationsMs(FrameRate(30000, 1001))

        assertEquals(1001L, durations.sum())
        assertEquals(List(9) { 100L } + 101L, durations)
    }

    @Test
    fun `even spacing rounds to the nearest frame and never repeats one`() {
        assertEquals(listOf(2, 5, 7, 10, 12, 15, 17, 20, 22), KeyFrames.even(2, 20, 8).boundaries)
        assertEquals(listOf(0, 1, 2, 3), KeyFrames.even(0, 3, 8).boundaries)
    }

    @Test
    fun `a moved boundary stays between its neighbours and inside the clip`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(2, 7, 9), keys.moved(1, 7, frameCount = 12).boundaries)
        assertEquals(listOf(2, 8, 9), keys.moved(1, 40, frameCount = 12).boundaries)
        assertEquals(listOf(2, 3, 9), keys.moved(1, 0, frameCount = 12).boundaries)
        assertEquals(listOf(0, 5, 9), keys.moved(0, -3, frameCount = 12).boundaries)
    }

    @Test
    fun `the last boundary reaches the clip's end, which is no frame of its own`() {
        assertEquals(listOf(2, 5, 12), KeyFrames(listOf(2, 5, 9)).moved(2, 99, frameCount = 12).boundaries)
    }

    @Test
    fun `an added boundary splits a key frame, or extends the ends`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(2, 4, 5, 9), keys.added(4, frameCount = 12).boundaries)
        assertEquals(listOf(0, 2, 5, 9), keys.added(0, frameCount = 12).boundaries)
        assertEquals(listOf(2, 5, 9, 12), keys.added(12, frameCount = 12).boundaries)
        assertEquals(keys, keys.added(5, frameCount = 12))
        assertFailsWith<IllegalArgumentException> { keys.added(13, frameCount = 12) }
    }

    @Test
    fun `a removed boundary lets the key frame before it last longer, and one key frame always stays`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(2, 9), keys.removed(1).boundaries)
        assertEquals(listOf(5, 9), keys.removed(0).boundaries)
        assertEquals(listOf(2, 5), keys.removed(2).boundaries)
        assertTrue(keys.canRemove)
        assertFalse(KeyFrames(listOf(2, 5)).canRemove)
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(2, 5)).removed(0) }
    }

    @Test
    fun `every frame between the boundaries belongs to one key frame`() {
        val keys = KeyFrames(listOf(2, 5, 9))

        assertEquals(listOf(null, null, 0, 0, 0, 1, 1, 1, 1, null), (0..9).map { keys.keyFrameAt(it) })
    }

    @Test
    fun `boundaries must rise from frame zero or later`() {
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(3)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(-1, 4)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(3, 3, 5)) }
        assertFailsWith<IllegalArgumentException> { KeyFrames(listOf(5, 3)) }
    }
}
