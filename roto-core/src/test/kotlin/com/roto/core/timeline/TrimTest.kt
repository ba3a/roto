package com.roto.core.timeline

import com.roto.core.profile.TimeRange
import com.roto.core.video.FrameRate
import com.roto.core.video.VideoInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class TrimTest {

    private val video = VideoInfo(1920, 1080, 10_000, FrameRate(30, 1), rotation = 0)
    private val trim = Trim(video)

    @Test
    fun `a new clip starts on the whole video`() {
        assertEquals(TimeRange(0, 10_000), trim.whole)
    }

    @Test
    fun `handles land on frame starts`() {
        assertEquals(TimeRange(1_000, 10_000), trim.withStart(trim.whole, 1_010))
        assertEquals(TimeRange(0, 2_000), trim.withEnd(trim.whole, 1_990))
        assertEquals(1_033, trim.snap(1_040))
    }

    @Test
    fun `the range stays inside the video`() {
        assertEquals(trim.whole, trim.withEnd(trim.withEnd(trim.whole, 5_000), 99_000))
        assertEquals(0, trim.snap(0))
        assertEquals(10_000, trim.snap(99_000))
    }

    @Test
    fun `the start cannot pass the end, nor the end the start`() {
        val range = TimeRange(1_000, 2_000)

        assertEquals(TimeRange(1_966, 2_000), trim.withStart(range, 9_000))
        assertEquals(TimeRange(1_000, 1_033), trim.withEnd(range, 0))
        assertEquals(1, trim.frameCount(TimeRange(1_966, 2_000)))
    }

    @Test
    fun `dragged all the way, the end is the video's own even between frames`() {
        val short = VideoInfo(100, 100, 1_010, FrameRate(30, 1), rotation = 0)

        assertEquals(1_010, Trim(short).withEnd(TimeRange(0, 500), 2_000).endMs)
    }

    @Test
    fun `the last frame of a range starts one frame before its end`() {
        assertEquals(1_966, trim.lastFrame(TimeRange(1_000, 2_000)))
        assertEquals(1_000, trim.lastFrame(TimeRange(1_000, 1_033)))
    }

    @Test
    fun `frames are counted at the video's rate, fractional rates included`() {
        assertEquals(300, trim.frameCount(trim.whole))
        assertEquals(30, trim.frameCount(TimeRange(1_000, 2_000)))
        val ntsc = VideoInfo(100, 100, 10_000, FrameRate(30_000, 1_001), rotation = 0)
        assertEquals(300, Trim(ntsc).frameCount(TimeRange(0, 10_010)))
    }

    @Test
    fun `nearest frame inverts a frame's start at every rate`() {
        val rates = listOf(FrameRate(30, 1), FrameRate(30_000, 1_001), FrameRate(24_000, 1_001), FrameRate(240, 1))
        rates.forEach { rate ->
            (0..5_000).forEach { frame -> assertEquals(frame, rate.nearestFrame(rate.startMs(frame)), "$rate") }
        }
    }
}
