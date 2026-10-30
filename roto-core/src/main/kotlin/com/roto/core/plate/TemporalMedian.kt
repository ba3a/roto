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
        // Read in place: a clip's frames are the largest thing in memory, and a copy would double them.
        val samples = IntArray(frames.size)
        val channelMedian = { x: Int, y: Int, channel: (Int) -> Int ->
            frames.forEachIndexed { index, frame -> samples[index] = channel(frame[x, y]) }
            samples.sort()
            samples[(samples.size - 1) / 2]
        }
        return Raster.of(width, height) { x, y ->
            Argb.of(
                red = channelMedian(x, y, Argb::red),
                green = channelMedian(x, y, Argb::green),
                blue = channelMedian(x, y, Argb::blue),
                alpha = channelMedian(x, y, Argb::alpha),
            )
        }
    }
}
