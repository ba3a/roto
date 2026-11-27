package com.roto.core.draft

import com.roto.core.image.IndexedRaster
import com.roto.core.shade.DraftColours

/**
 * Finishes one key frame's draft, shaded at `factor` times the canvas: [MajorityReduce] to the
 * canvas, [OrphanCleanup], [RampDither] when the clip asks for it, and the [Outline] last, so neither
 * cleanup nor dither ever touches it. The hold compares a key frame's reduction with the previous key
 * frame's reduction, before cleanup, dither and outline: holding to the finished draft would carry the
 * outline's colour into the next frame's interior.
 */
object DraftFinisher {

    /**
     * The finished [Draft] of [shaded] colours and [zones] at [factor] times the canvas, drawn with
     * [colours] and [settings], held to [previous], the previous key frame's [reduce].
     */
    fun finish(
        shaded: IndexedRaster,
        zones: IndexedRaster,
        factor: Int,
        colours: DraftColours,
        settings: DraftSettings,
        previous: Draft? = null,
    ): Draft = complete(reduce(shaded, zones, factor, settings, previous), colours, settings)

    /** The [MajorityReduce] of [shaded] and [zones], held to [previous] by the [settings]' hold. */
    fun reduce(
        shaded: IndexedRaster,
        zones: IndexedRaster,
        factor: Int,
        settings: DraftSettings,
        previous: Draft?,
    ): Draft = MajorityReduce.reduce(shaded, zones, factor, previous, settings.hold)

    /** The [reduced] draft cleaned, dithered when [settings] ask, and outlined with [colours]. */
    fun complete(reduced: Draft, colours: DraftColours, settings: DraftSettings): Draft {
        val cleaned = OrphanCleanup.clean(reduced)
        val dithered = if (settings.dither) RampDither.dither(cleaned, colours.zones) else cleaned
        return Outline.draw(dithered, colours.zones, colours.outline)
    }
}
