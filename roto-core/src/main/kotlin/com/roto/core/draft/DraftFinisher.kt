package com.roto.core.draft

import com.roto.core.image.IndexedRaster
import com.roto.core.shade.DraftColours

/**
 * Finishes one key frame's draft, shaded at `factor` times the canvas: [MajorityReduce] to the
 * canvas, [OrphanCleanup], [RampDither] when the clip asks for it, and the [Outline] last, so neither
 * cleanup nor dither ever touches it.
 */
object DraftFinisher {

    fun finish(
        shaded: IndexedRaster,
        zones: IndexedRaster,
        factor: Int,
        colours: DraftColours,
        settings: DraftSettings,
    ): Draft {
        val cleaned = OrphanCleanup.clean(MajorityReduce.reduce(shaded, zones, factor))
        val dithered = if (settings.dither) RampDither.dither(cleaned, colours.zones) else cleaned
        return Outline.draw(dithered, colours.zones, colours.outline)
    }
}
