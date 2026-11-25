package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.HexColor
import com.roto.core.palette.RampSet

/**
 * The steps of one ramp a model uses.
 *
 * @property ramp the ramp's name
 * @property firstStep the lowest step used, counted from 1 at the darkest colour
 * @property lastStep the highest step used
 */
data class RampUse(val ramp: String, val firstStep: Int, val lastStep: Int) {
    init {
        require(firstStep in 1..lastStep) { "Steps $firstStep..$lastStep of '$ramp' are not a range" }
    }
}

/**
 * Which ramps and steps a model's colours come from.
 *
 * @property ramps the ramps the model uses, in the order of the [RampSet], each once
 * @property usesOutline whether the model uses the project's outline colour
 * @property outside the figure colours that are in no ramp and are not the outline, as [Argb], ascending
 */
data class ModelColours(val ramps: List<RampUse>, val usesOutline: Boolean, val outside: List<Int>) {

    /**
     * The usage in words, such as `skin 1-3, cloth 2-5, outline`: a ramp with its used steps (one number
     * when only one step is used), then the outline, then the colours outside every ramp. Empty when the
     * model uses none of them.
     */
    fun summary(): String {
        val parts = ramps.map { use ->
            val steps = if (use.firstStep == use.lastStep) "${use.firstStep}" else "${use.firstStep}-${use.lastStep}"
            "${use.ramp} $steps"
        } + listOfNotNull("outline".takeIf { usesOutline }) +
            outside.map { "outside ${HexColor.format(it)}" }
        return parts.joinToString(", ")
    }
}

/** Reads which colours of figure pixels (alpha at least [FIGURE_ALPHA]) come from which ramp of [rampSet]. */
internal fun colourUsage(frames: List<Raster>, rampSet: RampSet): ModelColours {
    val used = sortedSetOf<Int>()
    for (frame in frames) {
        for (y in 0 until frame.height) {
            (0 until frame.width).map { x -> frame[x, y] }
                .filter { Argb.alpha(it) >= FIGURE_ALPHA }
                .mapTo(used, ::opaque)
        }
    }
    val inRamp = mutableSetOf<Int>()
    val uses = rampSet.ramps.mapNotNull { ramp ->
        val steps = ramp.colors.withIndex().filter { opaque(it.value) in used }
        inRamp += steps.map { opaque(it.value) }
        steps.takeIf { it.isNotEmpty() }?.let { RampUse(ramp.name, it.first().index + 1, it.last().index + 1) }
    }
    val outline = rampSet.outline?.let(::opaque)
    val usesOutline = outline != null && outline in used
    return ModelColours(uses, usesOutline, used.filter { it !in inRamp && it != outline })
}

private fun opaque(argb: Int): Int = argb or OPAQUE_BLACK

private val OPAQUE_BLACK = Argb.of(0, 0, 0)
