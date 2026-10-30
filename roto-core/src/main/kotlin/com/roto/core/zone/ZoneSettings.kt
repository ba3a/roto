package com.roto.core.zone

import com.roto.core.mask.BrushPoint
import kotlinx.serialization.Serializable

/** The most zones a clip may have: a label map holds them as indices 1..255, 0 being background. */
private const val MAX_ZONES = 255

/** The widest zone brush: the same as the mask brush. */
private const val MAX_BRUSH_RADIUS = 64

/**
 * How a clip's figure is split into zones — skin, shirt, trousers — each shaded with one ramp;
 * stored per clip in `profile.json`.
 *
 * Every masked pixel of every frame joins the zone whose [samples][Zone.samples] it is nearest to in
 * OKLab, with the lightness part of the distance scaled by [lightnessWeight]: below 1, the lit and
 * the shaded side of one material stay in one zone, while a change of colour still moves a pixel to
 * another. [fixes] are the artist's own word where that gets it wrong, on key frames.
 */
@Serializable
data class ZoneSettings(
    val zones: List<Zone> = emptyList(),
    val lightnessWeight: Double = 0.5,
    val fixes: List<ZoneStroke> = emptyList(),
) {
    init {
        require(zones.size <= MAX_ZONES) { "A clip may have at most $MAX_ZONES zones, this one ${zones.size}" }
        zones.groupBy { it.name }.values.firstOrNull { it.size > 1 }?.let { twice ->
            throw IllegalArgumentException("Zone '${twice.first().name}' is defined twice")
        }
        require(lightnessWeight in 0.0..1.0) { "lightnessWeight must be between 0 and 1, got $lightnessWeight" }
        fixes.firstOrNull { fix -> zones.none { it.name == fix.zone } }?.let { fix ->
            throw IllegalArgumentException("A zone fix names '${fix.zone}', which is not one of the zones")
        }
    }
}

/**
 * One stroke of the zone brush on [frame] of a clip: figure under a disc of [radius] pixels along
 * [points] joins [zone], whatever the nearest sample said. Strokes apply in the order painted.
 */
@Serializable
data class ZoneStroke(
    val frame: Int,
    val zone: String,
    val radius: Int,
    val points: List<BrushPoint>,
) {
    init {
        require(frame >= 0) { "A stroke's frame must not be negative, got $frame" }
        require(radius in 1..MAX_BRUSH_RADIUS) { "Brush radius must be between 1 and $MAX_BRUSH_RADIUS, got $radius" }
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }
}

/**
 * One zone of a figure: its [name], the [ramp] it is shaded with — one of the profile's ramps; two
 * zones may share one — and the [samples] the artist clicked on it.
 */
@Serializable
data class Zone(val name: String, val ramp: String, val samples: List<ZoneSample>) {
    init {
        require(name.isNotBlank()) { "A zone needs a name" }
        require(ramp.isNotBlank()) { "Zone '$name' needs a ramp" }
        require(samples.isNotEmpty()) { "Zone '$name' needs at least one sample" }
    }
}

/** A point the artist clicked: pixel ([x], [y]) of [frame] of the clip, in working-frame pixels. */
@Serializable
data class ZoneSample(val frame: Int, val x: Int, val y: Int) {
    init {
        require(frame >= 0 && x >= 0 && y >= 0) { "A sample's frame and coordinates must not be negative: $this" }
    }
}
