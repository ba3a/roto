package com.roto.core.profile

import com.roto.core.template.RelativePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a clip's clean plate — the empty background its figure is masked against — comes from.
 * Stored in `profile.json` as an object whose `type` names the kind.
 */
@Serializable
sealed interface PlateSource {

    /**
     * A [range] of the clip's own source video that shows only the empty wall, such as the few
     * seconds filmed before stepping into frame. The best plate when there is one.
     */
    @Serializable
    @SerialName("emptyRange")
    data class EmptyRange(val range: TimeRange) : PlateSource

    /**
     * The clip's own frames. For a walk across the frame, each pixel shows the wall in most frames,
     * so their median is the wall; a figure that stays on one spot for most of the clip needs an
     * [EmptyRange] instead.
     */
    @Serializable
    @SerialName("clipMedian")
    data object ClipMedian : PlateSource

    /**
     * A photo of the empty wall, [source] in the profile's `sources/` folder, taken from where the
     * figure was photographed. The one plate a still can have: it has no timeline to mark a range on,
     * and the median of its one frame is the figure itself.
     */
    @Serializable
    @SerialName("photo")
    data class Photo(val source: String) : PlateSource {
        init {
            RelativePath.validate(source, "wall photo")
        }
    }
}
