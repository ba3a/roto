package com.roto.core.zone

import kotlinx.serialization.Serializable

/** The most zones a clip may have: a label map holds them as indices 1..255, 0 being background. */
private const val MAX_ZONES = 255

/**
 * How a clip's figure is split into zones — skin, shirt, trousers — each shaded with one ramp;
 * stored per clip in `profile.json`.
 *
 * Every masked pixel of every frame joins the zone whose [samples][Zone.samples] it is nearest to in
 * OKLab, with the lightness part of the distance scaled by [lightnessWeight]: below 1, the lit and
 * the shaded side of one material stay in one zone, while a change of colour still moves a pixel to
 * another.
 */
@Serializable
data class ZoneSettings(
    val zones: List<Zone> = emptyList(),
    val lightnessWeight: Double = 0.5,
) {
    init {
        require(zones.size <= MAX_ZONES) { "A clip may have at most $MAX_ZONES zones, this one ${zones.size}" }
        zones.groupBy { it.name }.values.firstOrNull { it.size > 1 }?.let { twice ->
            throw IllegalArgumentException("Zone '${twice.first().name}' is defined twice")
        }
        require(lightnessWeight in 0.0..1.0) { "lightnessWeight must be between 0 and 1, got $lightnessWeight" }
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
