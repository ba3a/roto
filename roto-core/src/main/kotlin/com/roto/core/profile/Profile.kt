package com.roto.core.profile

import com.roto.core.cycle.CycleAnalysis
import com.roto.core.cycle.CycleSettings
import com.roto.core.cycle.KeyFrames
import com.roto.core.draft.DraftSettings
import com.roto.core.framebox.ScaleSettings
import com.roto.core.mask.BrushStroke
import com.roto.core.mask.MaskSettings
import com.roto.core.shade.ShadeSettings
import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import com.roto.core.track.RootSettings
import com.roto.core.zone.ZoneSettings
import kotlinx.serialization.Serializable

/**
 * One character (or prop) of a consuming project: the clips filmed for it, the palette ramps it
 * uses, and the [scale] it is drawn at once the artist has marked it. Stored as `profile.json` in
 * the profile's library folder; the folder name is the profile id, so the id is not repeated inside
 * the file. Validated on construction, including that every zone of every clip uses one of [ramps].
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
        clips.forEach { clip ->
            clip.zoning.zones.firstOrNull { it.ramp !in ramps }?.let { zone ->
                throw IllegalArgumentException(
                    "Zone '${zone.name}' of ${clip.id} uses ramp '${zone.ramp}', " +
                        "which is not one of the profile's ramps $ramps",
                )
            }
        }
    }

    /** The clip for [action] facing [facing], or null when none has been set up. */
    fun clip(action: String, facing: String): Clip? = clips.firstOrNull { it.action == action && it.facing == facing }

    /** This profile with [clip] in place of the one for its action and facing, or added last if there is none. */
    fun withClip(clip: Clip): Profile =
        if (clip(clip.action, clip.facing) == null) {
            copy(clips = clips + clip)
        } else {
            copy(clips = clips.map { if (it.id == clip.id) clip else it })
        }
}

/**
 * One action filmed from one facing: a [trim]med range of a [source] video in the profile's
 * `sources/` folder. [plate] says where its empty background comes from, [mask] how its figure is
 * told apart from it, [root] how the figure is tracked and held still, and [cycle] how its key frames
 * are proposed; [markers], once the artist has set them, replace that proposal as [KeyFrames]
 * boundaries. [fixes] are the artist's brush strokes on the masks of key frames. [zoning] splits the figure into zones, each shaded with one of the profile's ramps
 * across the lightness range [shading] sets, then finished as [draft] says.
 * [loop] says whether the published animation repeats; [mirror] opts this clip in to also being
 * published, mirrored, as the facing `roto.json` pairs it with.
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
    val fixes: List<BrushStroke> = emptyList(),
    val zoning: ZoneSettings = ZoneSettings(),
    val shading: ShadeSettings = ShadeSettings(),
    val draft: DraftSettings = DraftSettings(),
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
    fun keyFrames(proposal: () -> CycleAnalysis): KeyFrames = markers?.let { KeyFrames(it) } ?: proposal().keyFrames

    /** Whether anything here counts frames of the clip, which a [recut] would lose. */
    val hasFrameWork: Boolean get() = markers != null || fixes.isNotEmpty() || zoning.zones.isNotEmpty()

    /**
     * This clip cut from [source] over [trim] instead. [markers], [fixes] and [zoning] samples count
     * frames of the clip, so a new cut leaves them pointing at other pictures and they are dropped; a
     * plate marked in another video goes too. Every other setting stays, and an unchanged cut changes
     * nothing.
     */
    fun recut(source: String, trim: TimeRange): Clip {
        if (source == this.source && trim == this.trim) return this
        return copy(
            source = source,
            trim = trim,
            plate = if (source == this.source) plate else PlateSource.ClipMedian,
            markers = null,
            fixes = emptyList(),
            zoning = zoning.copy(zones = emptyList(), fixes = emptyList()),
        )
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
