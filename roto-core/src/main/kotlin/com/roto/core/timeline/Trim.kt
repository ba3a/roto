package com.roto.core.timeline

import com.roto.core.profile.TimeRange
import com.roto.core.video.VideoInfo

/**
 * The rules of trimming one video, whatever draws the handles: they land on frame starts, the range
 * never leaves the video, and it always holds at least one frame, so the start cannot pass the end.
 */
class Trim(private val info: VideoInfo) {

    /** The whole video, where a new clip's handles start. */
    val whole: TimeRange get() = TimeRange(0, info.durationMs)

    /** [range] with its start moved as near [ms] as these rules allow. */
    fun withStart(range: TimeRange, ms: Long): TimeRange {
        val frame = minOf(frameAt(ms), frameAt(range.endMs) - 1).coerceAtLeast(0)
        return TimeRange(info.frameRate.startMs(frame), range.endMs)
    }

    /** [range] with its end moved as near [ms] as these rules allow. */
    fun withEnd(range: TimeRange, ms: Long): TimeRange {
        val frame = maxOf(frameAt(ms), frameAt(range.startMs) + 1)
        val end = if (ms >= info.durationMs) info.durationMs else info.frameRate.startMs(frame)
        return TimeRange(range.startMs, end.coerceAtMost(info.durationMs))
    }

    /** The frame start nearest [ms], inside the video. */
    fun snap(ms: Long): Long = info.frameRate.startMs(frameAt(ms.coerceIn(0, info.durationMs)))

    /** When the last frame of [range] starts: the range's end is exclusive, so not at the end itself. */
    fun lastFrame(range: TimeRange): Long =
        info.frameRate.startMs((frameAt(range.endMs) - 1).coerceAtLeast(frameAt(range.startMs)))

    /** How many frames [range] holds at the video's frame rate. */
    fun frameCount(range: TimeRange): Int = (frameAt(range.endMs) - frameAt(range.startMs)).coerceAtLeast(1)

    private fun frameAt(ms: Long): Int = info.frameRate.nearestFrame(ms)
}
