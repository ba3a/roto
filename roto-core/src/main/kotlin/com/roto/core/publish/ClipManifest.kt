package com.roto.core.publish

import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import kotlinx.serialization.Serializable

/**
 * The small, generic manifest Roto publishes beside each clip's frames, for the consuming project's
 * own tools to read: which clip it is, which filmed clip it came from — the same one, or its mirror
 * — whether it loops, and its frames in order with how long each shows.
 *
 * Frame paths are relative to the project root, with `/` separators, whatever the output template
 * does with folders. Field names are part of the contract with consuming projects; do not rename them.
 */
@Serializable
data class ClipManifest(
    val action: String,
    val facing: String,
    val source: String,
    val mirrored: Boolean,
    val loop: Boolean,
    val frames: List<ManifestFrame>,
) {
    init {
        SafeName.validate(action, "action")
        SafeName.validate(facing, "facing")
        SafeName.validate(source, "source clip")
        require(frames.isNotEmpty()) { "A published clip has at least one frame" }
    }
}

/** One published frame: its [file] relative to the project root, shown for [durationMs]. */
@Serializable
data class ManifestFrame(val file: String, val durationMs: Int) {
    init {
        RelativePath.validate(file, "frame file")
        require(durationMs >= 1) { "A frame shows for at least 1 ms, got $durationMs" }
    }
}
