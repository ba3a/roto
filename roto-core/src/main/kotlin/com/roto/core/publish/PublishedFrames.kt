package com.roto.core.publish

import com.roto.core.ase.AseSprite
import com.roto.core.profile.Clip

/**
 * The frames a clip publishes: its handoff's art, retimed by the clip's [Retime][Clip.retime], and for
 * a mirrored facing then flipped. [Publisher] writes these and the coverage reader compares against
 * them, so the two cannot disagree about what a publish holds.
 */
object PublishedFrames {

    /** The frames of the filmed facing of [clip], whose handoff is [sprite]. */
    fun of(clip: Clip, sprite: AseSprite): List<ArtFrame> =
        clip.retime.apply(ArtFrames.read(sprite, clip.id), clip.id)

    /** [frames] as the mirrored facing publishes them: flipped, with the same durations. */
    fun mirrored(frames: List<ArtFrame>): List<ArtFrame> =
        frames.map { ArtFrame(Mirror.flip(it.image), it.durationMs) }
}
