package com.roto.core.video

/**
 * What `ffprobe` reports about a video's first video stream.
 *
 * [width] and [height] are as displayed: a portrait phone video stored sideways with a 90° [rotation]
 * reports its upright size, the same size `ffmpeg` decodes its frames at. [rotation] is clockwise
 * degrees, 0, 90, 180 or 270.
 */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val frameRate: FrameRate,
    val rotation: Int,
) {
    init {
        require(width > 0 && height > 0) { "Video size must be positive, got ${width}x$height" }
        require(durationMs > 0) { "Video duration must be positive, got $durationMs ms" }
        require(rotation in ROTATIONS) { "Rotation must be one of $ROTATIONS, got $rotation" }
    }

    private companion object {
        val ROTATIONS = listOf(0, 90, 180, 270)
    }
}
