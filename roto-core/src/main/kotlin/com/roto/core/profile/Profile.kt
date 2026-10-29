package com.roto.core.profile

import com.roto.core.cycle.CycleAnalysis
import com.roto.core.cycle.CycleSettings
import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.ScaleSettings
import com.roto.core.mask.MaskSettings
import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import com.roto.core.track.RootSettings
import kotlinx.serialization.Serializable

/**
 * One character (or prop) of a consuming project: the clips filmed for it, the palette ramps it
 * uses, and the [scale] it is drawn at once the artist has marked it. Stored as `profile.json` in the profile's library folder; the folder name is the profile id,
 * so the id is not repeated inside the file. Validated on construction.
 *
 * Clips are listed in the order the artist added them. At most one clip exists per action and
 * facing; their ids must differ even ignoring case, because each names a handoff file and Windows
 * and macOS file systems ignore case.
 */
@Serializable
data class Profile(
    val ramps: List<String> = emptyList(),
    val scale: ScaleSettings? = null,
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
 * `sources/` folder. [plate] says where its empty background comes from, [mask] how its figure is
 * told apart from it, [root] how the figure is tracked and held still, and [cycle] how its key frames
 * are proposed; [markers], once the artist has set them, replace that proposal as [KeyFrames]
 * boundaries. [loop] says whether the published animation repeats; [mirror] opts this clip in to
 * also being published, mirrored, as the facing `roto.json` pairs it with.
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
    val plate: PlateSource = PlateSource.ClipMedian,
    val mask: MaskSettings = MaskSettings(),
    val root: RootSettings = RootSettings(),
    val cycle: CycleSettings = CycleSettings(),
    val markers: List<Int>? = null,
    val loop: Boolean = true,
    val mirror: Boolean = false,
) {
    /** `<action>_<facing>`: names the clip's handoff file and its published manifest's source clip. */
    val id: String get() = "${action}_$facing"

    init {
        SafeName.validate(action, "action")
        SafeName.validate(facing, "facing")
        RelativePath.validate(source, "source")
        markers?.let { KeyFrames(it) }
    }

    /** The key frames drawn: the artist's [markers] when set, otherwise the cycle stage's [proposal]. */
    fun keyFrames(proposal: CycleAnalysis): KeyFrames = markers?.let { KeyFrames(it) } ?: proposal.keyFrames
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
