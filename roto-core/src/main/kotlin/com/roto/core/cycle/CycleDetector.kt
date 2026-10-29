package com.roto.core.cycle

import com.roto.core.image.Mask
import com.roto.core.track.HorizontalShift
import com.roto.core.track.RootTrack
import com.roto.core.video.FrameRate

/**
 * Finds a clip's cycle and proposes its key frames:
 *
 * 1. the masks are shifted by the [RootTrack], so a figure walking across stands still;
 * 2. the [RepeatPeriod] of those masks within the settings' period bounds;
 * 3. the [ContactFinder]'s contact frames at that repeat;
 * 4. [CycleSettings.keyFrames] key frames spread evenly over one cycle — the repeat times
 *    [CycleSettings.repeatsPerCycle] — starting at the first contact that leaves a whole cycle.
 *
 * A clip that does not loop, or is too short to repeat within the bounds, gets its key frames spread
 * evenly across its whole length instead.
 */
object CycleDetector {

    private const val MS_PER_SECOND = 1000L

    fun analyse(
        masks: List<Mask>,
        track: RootTrack,
        frameRate: FrameRate,
        settings: CycleSettings,
        loop: Boolean,
    ): CycleAnalysis {
        require(masks.isNotEmpty()) { "A clip needs at least one frame" }
        require(track.shifts.size == masks.size) { "Root track has ${track.shifts.size} frames, masks ${masks.size}" }
        val evenly = KeyFrames.even(0, masks.size, settings.keyFrames)
        val wholeClip = CycleAnalysis(repeatFrames = null, repeatScore = null, emptyList(), evenly.boundaries)
        if (!loop) return wholeClip
        val aligned = masks.mapIndexed { frame, mask -> HorizontalShift.shift(mask, track.shifts[frame]) }
        val repeat = RepeatPeriod.of(aligned, lags(settings, frameRate)) ?: return wholeClip
        val contacts = ContactFinder.contacts(masks, repeat.frames, settings.contactBand)
        val cycle = minOf(repeat.frames * settings.repeatsPerCycle, masks.size)
        val start = contacts.firstOrNull { it + cycle <= masks.size } ?: (masks.size - cycle)
        val markers = KeyFrames.even(start, cycle, settings.keyFrames).boundaries
        return CycleAnalysis(repeat.frames, repeat.score, contacts, markers)
    }

    /** The settings' period bounds in whole frames at [frameRate]: the shortest rounded up, the longest down. */
    private fun lags(settings: CycleSettings, frameRate: FrameRate): IntRange {
        val perSecond = MS_PER_SECOND * frameRate.denominator
        val shortest = (settings.minPeriodMs.toLong() * frameRate.numerator + perSecond - 1) / perSecond
        val longest = settings.maxPeriodMs.toLong() * frameRate.numerator / perSecond
        return shortest.toInt()..longest.toInt()
    }
}
