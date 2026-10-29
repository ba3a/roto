package com.roto.core.cycle

import com.roto.core.image.Mask
import kotlin.math.abs

/**
 * The mask of a synthetic side-on walker, 40 rows tall, at [frame]: crossing the frame at [speed]
 * pixels a frame, legs scissoring in a 10-frame repeat — spread widest at frames 2, 12, 22, …,
 * together at 7, 17, 27, …. Each leg is three pixels wide and the two look alike, as in a real
 * silhouette.
 */
internal fun walker(frame: Int, speed: Int = 2, width: Int = 120): Mask {
    val centre = 20 + speed * frame
    val spread = abs(Math.floorMod(frame - 2, 10) - 5)
    return Mask.of(width, 40) { x, y ->
        when {
            y < 6 -> x in centre - 2..centre + 2
            y < 22 -> x in centre - 4..centre + 4
            else -> {
                val reach = spread * (y - 22) / 17
                x in centre - reach - 1..centre - reach + 1 || x in centre + reach - 1..centre + reach + 1
            }
        }
    }
}
