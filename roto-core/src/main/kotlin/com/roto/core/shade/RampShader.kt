package com.roto.core.shade

import com.roto.core.color.OkLab
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster

/**
 * Shades a frame: each pixel of a zone takes the ramp step its OKLab lightness falls in under the
 * clip's [shading], and so the palette label [zoneColours] gives that step. Works at whatever size
 * it is given, the frame and its zone map sampled alike.
 */
class RampShader(private val shading: ClipShading, private val zoneColours: List<List<Int>>) {
    init {
        require(shading.zones.size == zoneColours.size) {
            "Shading has ${shading.zones.size} zones, the colours ${zoneColours.size}"
        }
        shading.zones.indices.forEach { zone ->
            require(shading.zones[zone].steps == zoneColours[zone].size) {
                "Zone ${zone + 1} is shaded in ${shading.zones[zone].steps} steps " +
                    "but has ${zoneColours[zone].size} colours"
            }
        }
    }

    /** [frame] as palette labels, `0` wherever [zones] says background. */
    fun shade(frame: Raster, zones: IndexedRaster): IndexedRaster {
        require(frame.width == zones.width && frame.height == zones.height) { "Frame and zone map differ in size" }
        return IndexedRaster.of(frame.width, frame.height) { x, y ->
            val zone = zones[x, y]
            if (zone == 0) 0 else zoneColours[zone - 1][shading.zones[zone - 1].step(OkLab.fromArgb(frame[x, y]).l)]
        }
    }
}
