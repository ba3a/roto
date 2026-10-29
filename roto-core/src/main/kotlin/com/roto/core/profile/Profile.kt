package com.roto.core.profile

import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import kotlinx.serialization.Serializable

/**
 * One character (or prop) of a consuming project: the clips filmed for it and the palette ramps it
 * uses. Stored as `profile.json` in the profile's library folder; the folder name is the profile id,
 * so the id is not repeated inside the file. Validated on construction.
 *
 * Clips are listed in the order the artist added them. At most one clip exists per action and
 * facing; their ids must differ even ignoring case, because each names a handoff file and Windows
 * and macOS file systems ignore case.
 */
@Serializable
data class Profile(
    val ramps: List<String> = emptyList(),
    val clips: List<Clip> = emptyList(),
) {
    init {
        ramps.forEach { require(it.isNotBlank()) { "ramps must not hold a blank name" } }
        require(ramps.toSet().size == ramps.size) { "ramps lists a ramp twice: $ramps" }
        clips.groupBy { it.id.lowercase() }.values.firstOrNull { it.size > 1 }?.let { duplicates ->
            throw IllegalArgumentException("Profile has more than one clip for ${duplicates.first().id}")
        }
    }

    /** The clip for [action] facing [facing], or null when none has been set up. */
    fun clip(action: String, facing: String): Clip? = clips.firstOrNull { it.action == action && it.facing == facing }
}

/**
 * One action filmed from one facing: a [trim]med range of a [source] video in the profile's
 * `sources/` folder. [loop] says whether the published animation repeats; [mirror] opts this clip in
 * to also being published, mirrored, as the facing `roto.json` pairs it with.
 *
 * [action] and [facing] come from the project's `roto.json`. They are not checked against it here,
 * so a profile still loads after the project renames an action; publishing such a clip fails instead.
 */
@Serializable
data class Clip(
    val action: String,
    val facing: String,
    val source: String,
    val trim: TimeRange,
    val loop: Boolean = true,
    val mirror: Boolean = false,
) {
    /** `<action>_<facing>`: names the clip's handoff file and its published manifest's source clip. */
    val id: String get() = "${action}_$facing"

    init {
        SafeName.validate(action, "action")
        SafeName.validate(facing, "facing")
        RelativePath.validate(source, "source")
    }
}

/** A half-open range of a video's timeline, [startMs] inclusive to [endMs] exclusive. */
@Serializable
data class TimeRange(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0) { "Range must not start before 0 ms, started at $startMs" }
        require(endMs > startMs) { "Range must end after it starts: $startMs..$endMs ms" }
    }

    val durationMs: Long get() = endMs - startMs
}
