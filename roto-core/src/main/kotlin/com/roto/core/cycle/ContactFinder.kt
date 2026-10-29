package com.roto.core.cycle

import com.roto.core.image.Mask

/**
 * Where in its repeat a figure's feet strike the ground: the contact pose, legs spread widest. The
 * spread is measured in the bottom band of each mask — its horizontal extent there — and averaged
 * over every frame at the same phase of the repeat, so one noisy frame cannot move the contact.
 */
object ContactFinder {

    /** How wide the legs spread in [mask]'s bottom [band] of its own height; 0 for an empty mask. */
    fun legSpread(mask: Mask, band: Double): Int {
        val rows = (0 until mask.height).filter { y -> (0 until mask.width).any { x -> mask[x, y] } }
        if (rows.isEmpty()) return 0
        val bottom = rows.last()
        val bandHeight = maxOf(1, (band * (bottom - rows.first() + 1)).toInt())
        val xs = (bottom - bandHeight + 1..bottom).flatMap { y -> (0 until mask.width).filter { x -> mask[x, y] } }
        return if (xs.isEmpty()) 0 else xs.max() - xs.min()
    }

    /** Every contact frame of [masks], which repeat every [repeat] frames: one per repeat. */
    fun contacts(masks: List<Mask>, repeat: Int, band: Double): List<Int> {
        require(repeat >= 1) { "Repeat must be at least one frame, got $repeat" }
        val spreads = masks.map { legSpread(it, band) }
        val phase = (0 until minOf(repeat, masks.size)).maxByOrNull { phase ->
            val atPhase = (phase until masks.size step repeat).map { spreads[it] }
            atPhase.sum().toDouble() / atPhase.size
        } ?: return emptyList()
        return (phase until masks.size step repeat).toList()
    }
}
