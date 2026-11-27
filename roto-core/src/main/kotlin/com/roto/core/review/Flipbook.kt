package com.roto.core.review

import com.roto.core.ase.AseSprite
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Raster
import com.roto.core.publish.ArtFrames
import com.roto.core.publish.Mirror

/** Which layer of the handoff a [FlipbookFrame] shows. */
enum class FlipbookSource {
    /** The artist's painting. */
    Art,

    /** Roto's draft, for a frame with no art yet. */
    Draft,

    /** The palette-mapped reference, for a frame with neither art nor draft. */
    Reference,
}

/**
 * One frame as the player shows it.
 *
 * @property image the frame as RGBA at canvas size
 * @property durationMs how long the frame shows, in milliseconds
 * @property source the layer [image] was taken from
 */
class FlipbookFrame(val image: Raster, val durationMs: Int, val source: FlipbookSource)

/**
 * A clip's frames as the player shows them: per frame the art where painted, else the draft, else
 * the reference. Cel opacity is ignored, so a reference shows at full strength.
 *
 * @property frames the frames in order
 * @property loop whether the clip plays again from its first frame after its last
 */
class Flipbook(val frames: List<FlipbookFrame>, val loop: Boolean) {

    /** The duration of every frame, in order. */
    val durations: List<Int> get() = frames.map { it.durationMs }

    /** Builds flipbooks from handoffs. */
    companion object {

        /** The flipbook of [sprite], the handoff of [clip]; each frame is flipped through [Mirror] when [mirrored]. */
        fun of(sprite: AseSprite, clip: String, loop: Boolean, mirrored: Boolean = false): Flipbook {
            val art = layer(sprite, HandoffMerge.ART, clip)
            val draft = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
            val reference = layer(sprite, HandoffMerge.REFERENCE, clip)
            val frames = sprite.frames.map { frame ->
                fun read(index: Int) = ArtFrames.layerPixels(sprite, frame, index, withCelOpacity = index == art)
                val artPixels = read(art)
                val draftPixels = if (draft >= 0) read(draft) else null
                val (pixels, source) = when {
                    artPixels.any(ArtFrames::isPainted) -> artPixels to FlipbookSource.Art
                    draftPixels != null && draftPixels.any(ArtFrames::isPainted) ->
                        draftPixels to FlipbookSource.Draft
                    else -> read(reference) to FlipbookSource.Reference
                }
                val image = Raster.fromArgbArray(sprite.width, sprite.height, pixels)
                FlipbookFrame(if (mirrored) Mirror.flip(image) else image, frame.durationMs, source)
            }
            return Flipbook(frames, loop)
        }

        private fun layer(sprite: AseSprite, name: String, clip: String): Int {
            val index = sprite.layers.indexOfFirst { it.name == name }
            require(index >= 0) { "The handoff of $clip has no '$name' layer" }
            return index
        }
    }
}
