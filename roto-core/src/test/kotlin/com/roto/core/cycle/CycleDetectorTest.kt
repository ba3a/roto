package com.roto.core.cycle

import com.roto.core.track.HorizontalShift
import com.roto.core.track.RootSettings
import com.roto.core.track.Stabiliser
import com.roto.core.video.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleDetectorTest {

    private val rate = FrameRate(25, 1)
    private val walk = (0 until 40).map { walker(it) }
    private val track = Stabiliser.track(walk, RootSettings())

    @Test
    fun `a walk across repeats every step, with a contact at the widest stride`() {
        val analysis = CycleDetector.analyse(walk, track, rate, CycleSettings(), loop = true)

        assertEquals(10, analysis.repeatFrames)
        assertEquals(1.0, analysis.repeatScore)
        assertEquals(listOf(2, 12, 22, 32), analysis.contacts)
    }

    @Test
    fun `a cycle of two steps gets its key frames spread over both, from a contact`() {
        val analysis = CycleDetector.analyse(walk, track, rate, CycleSettings(repeatsPerCycle = 2), loop = true)

        assertEquals(listOf(2, 5, 7, 10, 12, 15, 17, 20, 22), analysis.markers)
        assertEquals(listOf(2, 5, 7, 10, 12, 15, 17, 20), analysis.keyFrames.frames)
    }

    @Test
    fun `one step per cycle and fewer key frames`() {
        val settings = CycleSettings(keyFrames = 4)

        assertEquals(listOf(2, 5, 7, 10, 12), CycleDetector.analyse(walk, track, rate, settings, loop = true).markers)
    }

    @Test
    fun `masks are aligned on the root before they are compared`() {
        val unaligned = RepeatPeriod.of(walk, 8..20)
        val aligned = RepeatPeriod.of(walk.indices.map { HorizontalShift.shift(walk[it], track.shifts[it]) }, 8..20)

        assertEquals(1.0, aligned?.score)
        assertTrue(checkNotNull(unaligned).score < 0.5, "unaligned overlap ${unaligned.score}")
    }

    @Test
    fun `a clip that does not loop is spread evenly end to end`() {
        val analysis = CycleDetector.analyse(walk, track, rate, CycleSettings(), loop = false)

        assertEquals(null, analysis.repeatFrames)
        assertEquals(emptyList(), analysis.contacts)
        assertEquals((0..40 step 5).toList(), analysis.markers)
    }

    @Test
    fun `a looping clip too short to repeat is spread evenly too`() {
        val short = walk.take(12)

        val shortTrack = Stabiliser.track(short, RootSettings())
        val analysis = CycleDetector.analyse(short, shortTrack, rate, CycleSettings(), loop = true)

        assertEquals(null, analysis.repeatFrames)
        assertEquals(listOf(0, 2, 3, 5, 6, 8, 9, 11, 12), analysis.markers)
    }
}
