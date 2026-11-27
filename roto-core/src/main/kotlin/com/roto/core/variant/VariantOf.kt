package com.roto.core.variant

import com.roto.core.palette.HexColor
import com.roto.core.palette.HexColorSerializer
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Profile
import com.roto.core.template.SafeName
import kotlinx.serialization.Serializable

/**
 * A profile with no clips of its own: it publishes the finished art of the [base] profile with some
 * ramps swapped for others. Stored as `variant` in the variant's `profile.json`.
 *
 * Construction checks what needs nothing else; [check] checks the rest against the project and the
 * base profile.
 *
 * @property base the id of the profile whose art is recoloured
 * @property swaps the ramp swaps, each source ramp once
 * @property outline the palette colour the outline becomes, or null to keep it
 */
@Serializable
data class VariantOf(
    val base: String,
    val swaps: List<RampSwapPair>,
    val outline: @Serializable(with = HexColorSerializer::class) Int? = null,
) {
    init {
        SafeName.validate(base, "variant.base")
        swaps.groupBy { it.from }.values.firstOrNull { it.size > 1 }?.let { twice ->
            throw IllegalArgumentException("variant.swaps swaps ramp '${twice.first().from}' twice")
        }
    }

    /**
     * Checks this variant against the project's [colours] and the [base] profile as loaded, null when
     * there is none: the base must exist and not be a variant itself, every swapped ramp must be in
     * `ramps.json` and the outline colour in the palette. Throws [IllegalArgumentException] naming the field.
     */
    fun check(colours: ProjectPalette, base: Profile?) {
        checkBase(base)
        swaps.forEach { swap ->
            requireNotNull(colours.ramps.ramp(swap.from)) {
                "variant.swaps.from names ramp '${swap.from}', which is not in ramps.json"
            }
            requireNotNull(colours.ramps.ramp(swap.to)) {
                "variant.swaps.to names ramp '${swap.to}', which is not in ramps.json"
            }
        }
        outline?.let {
            require(it in colours.palette) { "variant.outline ${HexColor.format(it)} is not in the palette" }
        }
    }

    /** Checks only [base]: it must exist, in this project, and must not be a variant itself. */
    fun checkBase(base: Profile?) {
        requireNotNull(base) { "variant.base names profile '${this.base}', which does not exist in this project" }
        require(base.variant == null) {
            "variant.base names profile '${this.base}', which is itself a variant of '${base.variant?.base}'"
        }
    }
}

/**
 * One ramp swap of a [VariantOf]: art coloured from ramp [from] is coloured from ramp [to] instead.
 *
 * @property from the ramp the base's art uses
 * @property to the ramp the variant uses in its place
 */
@Serializable
data class RampSwapPair(val from: String, val to: String) {
    init {
        require(from.isNotBlank()) { "variant.swaps.from must not be blank" }
        require(to.isNotBlank()) { "variant.swaps.to must not be blank" }
    }
}
