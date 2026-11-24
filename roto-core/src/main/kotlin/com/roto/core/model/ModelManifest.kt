package com.roto.core.model

import com.roto.core.template.SafeName
import kotlinx.serialization.Serializable

/**
 * A model's `model.json`: everything about a [ModelClip] except its frame pictures, which sit beside
 * it as `000.png`, `001.png`, and so on.
 *
 * @property action the action, a [SafeName]
 * @property facing the facing, a [SafeName]
 * @property loop whether the animation repeats
 * @property durations each frame's duration in ms, at least 1; one entry per frame file
 * @property importedFrom where the model was imported from
 */
@Serializable
data class ModelManifest(
    val action: String,
    val facing: String,
    val loop: Boolean,
    val durations: List<Int>,
    val importedFrom: String,
) {
    init {
        SafeName.validate(action, "action")
        SafeName.validate(facing, "facing")
        require(durations.isNotEmpty()) { "A model needs at least one frame duration" }
        durations.firstOrNull { it < 1 }?.let { throw IllegalArgumentException("A frame lasts at least 1 ms, got $it") }
    }

    /** `<action>_<facing>`: the model's folder name. */
    val key: String get() = "${action}_$facing"
}
