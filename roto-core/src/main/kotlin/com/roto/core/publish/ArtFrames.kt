package com.roto.core.publish

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseFrame
import com.roto.core.ase.AseSprite
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Argb
import com.roto.core.image.Raster

/** One published frame: what the artist painted, as RGBA at canvas size, shown for [durationMs]. */
class ArtFrame(val image: Raster, val durationMs: Int)

/**
 * Reads what gets published from a handoff: the [art][HandoffMerge.ART] layer of every frame, as RGBA
 * at the sprite's size, with the frame's duration as the file holds it. The reference, the draft and
 * any other layer are the artist's working material and stay in the handoff.
 *
 * A pixel takes its palette colour, transparent where the index is the sprite's transparent one; a
 * cel at reduced opacity scales its pixels' alpha to match. A frame with no art yet stops the
 * publish rather than ship a blank frame.
 */
object ArtFrames {

    private const val FULL = 255

    fun read(sprite: AseSprite, clip: String): List<ArtFrame> {
        val art = artLayer(sprite, clip)
        return sprite.frames.mapIndexed { index, frame ->
            val pixels = pixels(sprite, frame, art)
            require(pixels.any(::isPainted)) { "Frame ${index + 1} of $clip has no art yet" }
            ArtFrame(Raster.fromArgbArray(sprite.width, sprite.height, pixels), frame.durationMs)
        }
    }

    /** How many of [sprite]'s frames hold art. */
    fun paintedCount(sprite: AseSprite, clip: String): Int {
        val art = artLayer(sprite, clip)
        return sprite.frames.count { frame -> pixels(sprite, frame, art).any(::isPainted) }
    }

    private fun artLayer(sprite: AseSprite, clip: String): Int {
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        require(art >= 0) { "The handoff of $clip has no '${HandoffMerge.ART}' layer" }
        return art
    }

    private fun pixels(sprite: AseSprite, frame: AseFrame, art: Int): IntArray {
        val pixels = IntArray(sprite.width * sprite.height)
        frame.cels.filter { it.layerIndex == art }.forEach { cel -> place(cel, sprite, pixels) }
        return pixels
    }

    private fun place(cel: AseCel, sprite: AseSprite, pixels: IntArray) {
        for (y in 0 until cel.image.height) {
            for (x in 0 until cel.image.width) {
                val canvasX = cel.x + x
                val canvasY = cel.y + y
                val entry = cel.image[x, y]
                if (canvasX !in 0 until sprite.width || canvasY !in 0 until sprite.height) continue
                if (entry == sprite.transparentIndex) continue
                pixels[canvasY * sprite.width + canvasX] = withOpacity(sprite.palette[entry], cel.opacity)
            }
        }
    }

    private fun isPainted(argb: Int): Boolean = Argb.alpha(argb) != 0

    private fun withOpacity(argb: Int, opacity: Int): Int {
        if (opacity == FULL) return argb
        val alpha = (Argb.alpha(argb) * opacity + FULL / 2) / FULL
        return Argb.of(Argb.red(argb), Argb.green(argb), Argb.blue(argb), alpha)
    }
}
