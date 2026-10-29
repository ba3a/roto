package com.roto.core.framebox

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import kotlin.math.floor

/**
 * Renders what one frame shows inside its [FrameBoxes] box, at [factor][sample] times the canvas
 * size — the draft renders at four times the target and reduces later. Each output pixel takes the
 * frame pixel under its centre, nearest-neighbour: no resampling filter, so every output colour or
 * label is one the frame really held. What lies beyond the frame's edge is empty.
 */
object CanvasSampler {

    private const val HALF = 0.5
    private const val OUTSIDE = -1

    fun sample(raster: Raster, boxes: FrameBoxes, frame: Int, factor: Int): Raster {
        val pixels = raster.toArgbArray()
        val sources = sources(boxes, frame, factor, raster.width, raster.height)
        val sampled = IntArray(sources.size) { if (sources[it] == OUTSIDE) Argb.TRANSPARENT else pixels[sources[it]] }
        return Raster.fromArgbArray(boxes.canvas.width * factor, boxes.canvas.height * factor, sampled)
    }

    fun sample(mask: Mask, boxes: FrameBoxes, frame: Int, factor: Int): Mask {
        val bits = mask.toBooleanArray()
        val sources = sources(boxes, frame, factor, mask.width, mask.height)
        val sampled = BooleanArray(sources.size) { sources[it] != OUTSIDE && bits[sources[it]] }
        return Mask.fromBooleanArray(boxes.canvas.width * factor, boxes.canvas.height * factor, sampled)
    }

    /**
     * For each output pixel, row-major, the row-major offset of the `width × height` frame pixel
     * under its centre, or [OUTSIDE] when that lies beyond the frame.
     */
    private fun sources(boxes: FrameBoxes, frame: Int, factor: Int, width: Int, height: Int): IntArray {
        require(factor >= 1) { "Factor must be at least 1, got $factor" }
        val outputWidth = boxes.canvas.width * factor
        val outputHeight = boxes.canvas.height * factor
        val columns = IntArray(outputWidth) { u -> floor(boxes.frameX(frame, (u + HALF) / factor)).toInt() }
        val rows = IntArray(outputHeight) { v -> floor(boxes.frameY((v + HALF) / factor)).toInt() }
        return IntArray(outputWidth * outputHeight) { offset ->
            val x = columns[offset % outputWidth]
            val y = rows[offset / outputWidth]
            if (x in 0 until width && y in 0 until height) y * width + x else OUTSIDE
        }
    }
}
