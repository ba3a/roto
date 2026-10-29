package com.roto.core.palette

import com.roto.core.color.OkLab
import kotlinx.serialization.Serializable

/**
 * A project's `ramps.json`: its named [ramps] and, optionally, the [outline] colour drafts are
 * outlined in. Without one, each region is outlined in its own ramp's darkest colour.
 */
@Serializable
data class RampSet(
    val outline: @Serializable(with = HexColorSerializer::class) Int? = null,
    val ramps: List<Ramp>,
) {
    init {
        ramps.groupBy { it.name }.values.firstOrNull { it.size > 1 }?.let { twice ->
            throw IllegalArgumentException("Ramp '${twice.first().name}' is defined twice")
        }
    }

    /** The ramp called [name], or null when there is none. */
    fun ramp(name: String): Ramp? = ramps.firstOrNull { it.name == name }
}

/**
 * A named run of palette [colors] from darkest to lightest, as the artist shades one material: skin,
 * cloth, metal. Shading picks a step by lightness, so the colours must grow strictly lighter in
 * OKLab; the first is the darkest, which also outlines the ramp's regions.
 */
@Serializable
data class Ramp(
    val name: String,
    val colors: List<@Serializable(with = HexColorSerializer::class) Int>,
) {
    init {
        require(name.isNotBlank()) { "A ramp needs a name" }
        require(colors.isNotEmpty()) { "Ramp '$name' has no colours" }
        colors.zipWithNext().firstOrNull { (darker, lighter) -> lightness(darker) >= lightness(lighter) }?.let {
            throw IllegalArgumentException(
                "Ramp '$name' must run from dark to light, but ${HexColor.format(it.first)} is not darker than " +
                    HexColor.format(it.second),
            )
        }
    }

    /** The darkest colour, the first. */
    val darkest: Int get() = colors.first()
}

private fun lightness(argb: Int): Double = OkLab.fromArgb(argb).l
