package com.roto.core.handoff

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseFrame
import com.roto.core.ase.AseLayer
import com.roto.core.ase.AseSprite
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.palette.HexColor

/**
 * Builds a clip's handoff sprite from a [HandoffRender], keeping everything the artist made.
 *
 * The sprite is indexed; palette entry 0 is transparent and entries 1.. are the project palette. Its
 * layers, bottom to top, are [REFERENCE] — the source frame, at 40 % cel opacity, which every editor
 * honours — then [DRAFT], then the artist's layers: [ART] and any other the artist added.
 *
 * Re-rendering over an existing handoff rewrites what the reference and draft layers show, keeping
 * whether the artist hid them. Every other layer is the artist's and comes through as it was: same
 * name, flags, cels and positions, and the same colours — its indices are remapped by colour when the
 * palette has changed. Where that is impossible the merge refuses rather than alter the art: the
 * canvas changed size, a colour the art uses left the palette, or fewer key frames would drop a frame
 * that holds art.
 */
object HandoffMerge {

    const val REFERENCE = "reference"
    const val DRAFT = "draft"
    const val ART = "art"

    /** 40 % of full opacity. */
    private const val REFERENCE_OPACITY = 102

    fun merge(existing: AseSprite?, render: HandoffRender): AseSprite {
        val palette = listOf(Argb.TRANSPARENT) + render.palette.colors
        val previous = existing?.let { Previous(it, render, palette) }
        val artistLayers = previous?.artistLayers ?: listOf(AseLayer(ART))
        val layers = listOf(
            AseLayer(REFERENCE, visible = previous?.visible(REFERENCE) ?: true, editable = false),
            AseLayer(DRAFT, visible = previous?.visible(DRAFT) ?: true),
        ) + artistLayers
        val frames = render.frames.mapIndexed { index, frame ->
            val roto = listOf(
                AseCel(0, frame.reference, opacity = REFERENCE_OPACITY),
                AseCel(1, frame.draft),
            )
            AseFrame(frame.durationMs, roto + (previous?.artistCels(index, firstLayer = 2) ?: emptyList()))
        }
        return AseSprite(render.width, render.height, palette, layers, frames, transparentIndex = 0)
    }

    /** What an existing handoff holds that a re-render must keep. */
    private class Previous(private val sprite: AseSprite, render: HandoffRender, palette: List<Int>) {

        private val artistIndices = sprite.layers.indices.filter { sprite.layers[it].name !in setOf(REFERENCE, DRAFT) }
        private val remap: IntArray

        val artistLayers: List<AseLayer> =
            artistIndices.map { sprite.layers[it] }.ifEmpty { listOf(AseLayer(ART)) }

        init {
            require(sprite.width == render.width && sprite.height == render.height) {
                "The canvas is now ${render.width}x${render.height}, " +
                    "but the handoff is ${sprite.width}x${sprite.height}; its art cannot be kept"
            }
            val used = usedIndices()
            remap = IntArray(sprite.palette.size) { index ->
                val color = sprite.palette[index]
                when {
                    index == sprite.transparentIndex || Argb.alpha(color) == 0 -> 0
                    index !in used -> 0
                    else -> palette.indexOf(color).takeIf { it > 0 } ?: throw IllegalArgumentException(
                        "The art uses ${HexColor.format(color)}, which is no longer in the palette",
                    )
                }
            }
            sprite.frames.drop(render.frames.size).forEachIndexed { offset, frame ->
                require(frame.cels.none { it.layerIndex in artistIndices && it.hasPixels() }) {
                    "Frame ${render.frames.size + offset + 1} holds art, but the clip now has only " +
                        "${render.frames.size} key frames; move the markers back or clear that art first"
                }
            }
        }

        fun visible(name: String): Boolean? = sprite.layers.firstOrNull { it.name == name }?.visible

        /** The artist's cels of frame [index], their layers renumbered from [firstLayer]. */
        fun artistCels(index: Int, firstLayer: Int): List<AseCel> {
            val frame = sprite.frames.getOrNull(index) ?: return emptyList()
            return frame.cels.filter { it.layerIndex in artistIndices }.map { cel ->
                val image = IndexedRaster.of(cel.image.width, cel.image.height) { x, y -> remap[cel.image[x, y]] }
                cel.copy(layerIndex = firstLayer + artistIndices.indexOf(cel.layerIndex), image = image)
            }
        }

        private fun usedIndices(): Set<Int> =
            sprite.frames.flatMap { frame -> frame.cels.filter { it.layerIndex in artistIndices } }
                .flatMap { cel -> cel.image.toByteArray().map { it.toInt() and BYTE }.distinct() }
                .toSet()

        private fun AseCel.hasPixels(): Boolean =
            image.toByteArray().any { (it.toInt() and BYTE) != sprite.transparentIndex }

        private companion object {
            const val BYTE = 0xFF
        }
    }
}
