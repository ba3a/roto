package com.roto.core.plate

import com.roto.core.image.Argb
import com.roto.core.image.Raster

/**
 * The per-pixel median over time of equally sized frames, taken channel by channel. Whatever a pixel
 * shows in most frames wins, so a figure that passes over a pixel in fewer than half the frames
 * vanishes, and sensor noise on a still wall averages out. For an even number of frames the lower
 * of the two middle values is taken, so every output channel is one a frame actually held.
 */
object TemporalMedian {

    fun of(frames: List<Raster>): Raster {
        require(frames.isNotEmpty()) { "A median needs at least one frame" }
        val width = frames.first().width
        val height = frames.first().height
        frames.forEachIndexed { index, frame ->
            require(frame.width == width && frame.height == height) {
                "Frame $index is ${frame.width}x${frame.height}, expected ${width}x$height"
            }
        }
        val pixels = frames.map { it.toArgbArray() }
        val samples = IntArray(frames.size)
        val channelMedian = { offset: Int, channel: (Int) -> Int ->
            pixels.forEachIndexed { frame, frameArgb -> samples[frame] = channel(frameArgb[offset]) }
            samples.sort()
            samples[(samples.size - 1) / 2]
        }
        val median = IntArray(width * height) { offset ->
            Argb.of(
                red = channelMedian(offset, Argb::red),
                green = channelMedian(offset, Argb::green),
                blue = channelMedian(offset, Argb::blue),
                alpha = channelMedian(offset, Argb::alpha),
            )
        }
        return Raster.fromArgbArray(width, height, median)
    }
}
