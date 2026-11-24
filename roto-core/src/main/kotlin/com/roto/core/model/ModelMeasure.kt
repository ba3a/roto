package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.palette.RampSet
import com.roto.core.track.RootFinder
import com.roto.core.track.RootSettings

/** The least alpha that counts as figure; a fainter pixel, such as a soft shadow, is background. */
const val FIGURE_ALPHA = 128

/**
 * The inclusive pixel bounds of a figure in one frame.
 *
 * @property left the first column holding figure
 * @property top the first row holding figure
 * @property right the last column holding figure
 * @property bottom the last row holding figure, where the feet stand
 */
data class FigureBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** The box's height in rows. */
    val height: Int get() = bottom - top + 1
}

/**
 * What [ModelMeasure] finds in one frame.
 *
 * @property box the figure's alpha bounding box, or null when the frame holds no figure
 * @property rootX the root x from the hip band, or null when the frame holds no figure
 */
data class FrameMeasure(val box: FigureBox?, val rootX: Int?)

/**
 * What [ModelMeasure] finds in a whole clip.
 *
 * @property frames one measure per frame, in order
 * @property bodyHeight the clip's body height in rows, or null when no frame holds a figure
 * @property feetRow the clip's feet row, or null when no frame holds a figure
 */
data class ModelMeasurement(val frames: List<FrameMeasure>, val bodyHeight: Int?, val feetRow: Int?)

/**
 * Measures a model's geometry from its frames' alpha. A pixel with alpha of at least [FIGURE_ALPHA] is
 * figure. The clip's top and feet rows are the medians of its figure frames' own (the lower median of
 * an even count), so one stray frame does not move them; the body height is the span between them.
 * Integer arithmetic only, except the hip band that [RootFinder] already defines.
 */
object ModelMeasure {

    /** Measures one [frame]. */
    fun measureFrame(frame: Raster): FrameMeasure {
        val mask = Mask.of(frame.width, frame.height) { x, y -> Argb.alpha(frame[x, y]) >= FIGURE_ALPHA }
        val box = boxOf(mask) ?: return FrameMeasure(null, null)
        return FrameMeasure(box, RootFinder.rootX(mask, RootSettings())?.toInt())
    }

    /** Measures every frame of [frames] and the clip as a whole. */
    fun measure(frames: List<Raster>): ModelMeasurement {
        val measures = frames.map(::measureFrame)
        val boxes = measures.mapNotNull { it.box }
        if (boxes.isEmpty()) return ModelMeasurement(measures, null, null)
        val top = lowerMedian(boxes.map { it.top })
        val feet = lowerMedian(boxes.map { it.bottom })
        return ModelMeasurement(measures, feet - top + 1, feet)
    }

    /** Maps the colours the figure pixels of [frames] use to their ramp and step in [rampSet]. */
    fun colours(frames: List<Raster>, rampSet: RampSet): ModelColours = colourUsage(frames, rampSet)

    private fun boxOf(mask: Mask): FigureBox? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (mask[x, y]) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else FigureBox(left, top, right, bottom)
    }

    private fun lowerMedian(values: List<Int>): Int = values.sorted()[(values.size - 1) / 2]
}
