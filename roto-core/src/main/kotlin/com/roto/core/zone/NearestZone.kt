package com.roto.core.zone

import com.roto.core.color.OkLab
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Mask
import com.roto.core.image.Raster

/**
 * Splits a figure into zones by nearest prototype. Every [ZoneSample] is one prototype: the mean
 * OKLab colour of the 3×3 pixels around the click, so a single noisy pixel cannot decide it. Every
 * masked pixel then takes the zone of the prototype nearest to it, its lightness weighted as the
 * [ZoneSettings] say; of prototypes equally near, the first wins.
 *
 * A label map holds `0` for background and `i + 1` for the `i`th zone.
 */
object NearestZone {

    /** A [color] the artist sampled, standing for the zone labelled [label]. */
    data class Prototype(val label: Int, val color: OkLab)

    private const val PATCH_RADIUS = 1

    /** The prototypes of [settings]' zones, sampled from [frames], the clip's frames. */
    fun prototypes(frames: List<Raster>, settings: ZoneSettings): List<Prototype> {
        require(settings.zones.isNotEmpty()) { "No zones have been sampled yet" }
        return settings.zones.flatMapIndexed { index, zone ->
            zone.samples.map { sample -> Prototype(index + 1, patchColor(frames, sample, zone.name)) }
        }
    }

    /** [frame]'s label map: each pixel inside [mask] labelled with its nearest of [prototypes]. */
    fun label(frame: Raster, mask: Mask, prototypes: List<Prototype>, lightnessWeight: Double): IndexedRaster {
        require(frame.width == mask.width && frame.height == mask.height) {
            "Frame is ${frame.width}x${frame.height}, its mask ${mask.width}x${mask.height}"
        }
        require(prototypes.isNotEmpty()) { "Labelling needs at least one prototype" }
        return IndexedRaster.of(frame.width, frame.height) { x, y ->
            if (mask[x, y]) nearest(OkLab.fromArgb(frame[x, y]), prototypes, lightnessWeight) else 0
        }
    }

    private fun nearest(color: OkLab, prototypes: List<Prototype>, lightnessWeight: Double): Int {
        var best = prototypes.first()
        var bestDistance = Double.MAX_VALUE
        for (prototype in prototypes) {
            val lightness = lightnessWeight * (color.l - prototype.color.l)
            val a = color.a - prototype.color.a
            val b = color.b - prototype.color.b
            val distance = lightness * lightness + a * a + b * b
            if (distance < bestDistance) {
                best = prototype
                bestDistance = distance
            }
        }
        return best.label
    }

    private fun patchColor(frames: List<Raster>, sample: ZoneSample, zone: String): OkLab {
        require(sample.frame in frames.indices) {
            "Zone '$zone' is sampled on frame ${sample.frame}, but the clip has ${frames.size} frames"
        }
        val frame = frames[sample.frame]
        require(frame.contains(sample.x, sample.y)) {
            "Zone '$zone' is sampled at (${sample.x}, ${sample.y}), outside the ${frame.width}x${frame.height} frame"
        }
        val patch = (sample.y - PATCH_RADIUS..sample.y + PATCH_RADIUS).flatMap { y ->
            (sample.x - PATCH_RADIUS..sample.x + PATCH_RADIUS)
                .filter { x -> frame.contains(x, y) }
                .map { x -> OkLab.fromArgb(frame[x, y]) }
        }
        return OkLab(
            l = patch.sumOf { it.l } / patch.size,
            a = patch.sumOf { it.a } / patch.size,
            b = patch.sumOf { it.b } / patch.size,
        )
    }
}
