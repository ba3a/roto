package com.roto.app

import com.roto.core.video.FrameRate

private const val MS_PER_SECOND = 1000L
private const val MS_DIGITS = 3
private const val HUNDRED = 100L

/** [ms] as seconds with three decimals, written the same in every locale. */
internal fun seconds(ms: Long): String =
    "${ms / MS_PER_SECOND}.${(ms % MS_PER_SECOND).toString().padStart(MS_DIGITS, '0')} s"

internal fun clipCount(count: Int): String = if (count == 1) "1 clip" else "$count clips"

internal fun frameCount(count: Int): String = if (count == 1) "1 frame" else "$count frames"

/** [rate] the way a video's frame rate is usually read: `30 fps`, `29.97 fps`. */
internal fun framesPerSecond(rate: FrameRate): String {
    if (rate.numerator % rate.denominator == 0) return "${rate.numerator / rate.denominator} fps"
    val hundredths = rate.numerator * HUNDRED / rate.denominator
    return "${hundredths / HUNDRED}.${(hundredths % HUNDRED).toString().padStart(2, '0')} fps"
}
