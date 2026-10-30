package com.roto.core.zone

import com.roto.core.image.IndexedRaster
import com.roto.core.mask.BrushPath

/**
 * Applies the artist's [ZoneStroke]s to a label map: pure, like the mask brush. A stroke only
 * re-labels figure: what is background stays background, since the mask decides the silhouette.
 */
internal object ZoneBrush {

    /** [labels] with [strokes] applied in order; [labels] itself is left as it was. */
    fun apply(labels: IndexedRaster, strokes: List<ZoneStroke>, settings: ZoneSettings): IndexedRaster {
        if (strokes.isEmpty()) return labels
        val fixed = IndexedRaster.of(labels.width, labels.height) { x, y -> labels[x, y] }
        strokes.forEach { stroke ->
            val label = settings.zones.indexOfFirst { it.name == stroke.zone } + 1
            BrushPath.forEachPixel(stroke.points, stroke.radius, labels.width, labels.height) { x, y ->
                if (labels[x, y] != 0) fixed[x, y] = label
            }
        }
        return fixed
    }
}
