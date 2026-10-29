package com.roto.core.track

import kotlinx.serialization.Serializable

/**
 * Where a clip's figure stands, frame by frame. [rootX] is the root as found, null where a frame
 * showed no figure; [smoothedX] the gaps filled and the moving average taken; [shifts] the whole
 * pixels each frame moves right (left when negative) to hold the smoothed root at one place — all
 * zero when the clip is not stabilised.
 */
@Serializable
data class RootTrack(
    val rootX: List<Double?>,
    val smoothedX: List<Double>,
    val shifts: List<Int>,
) {
    init {
        require(rootX.isNotEmpty()) { "A root track needs at least one frame" }
        require(smoothedX.size == rootX.size && shifts.size == rootX.size) {
            "Root track lists differ in length: ${rootX.size}, ${smoothedX.size}, ${shifts.size}"
        }
    }
}
