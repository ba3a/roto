package com.roto.core.ase

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster

/**
 * An indexed-colour Aseprite sprite: the subset of the `.ase` format Roto writes and reads.
 *
 * [palette] holds up to 256 [Argb] colours; every cel index must point into it. [transparentIndex]
 * is the palette entry Aseprite and LibreSprite treat as transparent on non-background layers.
 * [layers] are listed bottom to top, the way the format stores them.
 */
data class AseSprite(
    val width: Int,
    val height: Int,
    val palette: List<Int>,
    val layers: List<AseLayer>,
    val frames: List<AseFrame>,
    val transparentIndex: Int = 0,
) {
    init {
        require(width in 1..AseLimits.MAX_DIMENSION && height in 1..AseLimits.MAX_DIMENSION) {
            "Sprite size must be 1..${AseLimits.MAX_DIMENSION} on each side, was ${width}x$height"
        }
        require(palette.size in 1..AseLimits.MAX_PALETTE_SIZE) {
            "Palette must hold 1..${AseLimits.MAX_PALETTE_SIZE} colours, had ${palette.size}"
        }
        require(transparentIndex in palette.indices) {
            "Transparent index $transparentIndex is outside the ${palette.size}-colour palette"
        }
        require(layers.isNotEmpty()) { "Sprite must have at least one layer" }
        require(layers.size <= AseLimits.MAX_COUNT) { "Too many layers: ${layers.size}" }
        require(frames.isNotEmpty()) { "Sprite must have at least one frame" }
        require(frames.size <= AseLimits.MAX_COUNT) { "Too many frames: ${frames.size}" }
        frames.forEachIndexed { frameIndex, frame -> validateCels(frameIndex, frame) }
    }

    private fun validateCels(frameIndex: Int, frame: AseFrame) {
        val seenLayers = HashSet<Int>()
        for (cel in frame.cels) {
            require(cel.layerIndex in layers.indices) {
                "Frame $frameIndex has a cel on layer ${cel.layerIndex}, but there are ${layers.size} layers"
            }
            require(seenLayers.add(cel.layerIndex)) {
                "Frame $frameIndex has more than one cel on layer ${cel.layerIndex}"
            }
            require(cel.image.maxIndex() < palette.size) {
                "Frame $frameIndex layer ${cel.layerIndex} uses index ${cel.image.maxIndex()}, " +
                    "outside the ${palette.size}-colour palette"
            }
        }
    }
}

/**
 * One layer. [opacity] is `0..255`. Layer opacity is a later Aseprite addition that older readers
 * may ignore; [AseCel.opacity] is understood everywhere, so prefer it for anything that must look
 * the same in every editor.
 */
data class AseLayer(
    val name: String,
    val opacity: Int = Argb.OPAQUE_ALPHA,
    val visible: Boolean = true,
    val editable: Boolean = true,
) {
    init {
        require(opacity in 0..Argb.OPAQUE_ALPHA) { "Layer opacity out of range: $opacity" }
        require(name.toByteArray(Charsets.UTF_8).size <= AseLimits.MAX_COUNT) { "Layer name too long: $name" }
    }
}

/** One animation frame, shown for [durationMs]. A layer with no cel in [cels] is empty in this frame. */
data class AseFrame(
    val durationMs: Int,
    val cels: List<AseCel>,
) {
    init {
        require(durationMs in 1..AseLimits.MAX_COUNT) { "Frame duration out of range: $durationMs ms" }
    }
}

/**
 * The image one layer shows in one frame, placed with its top-left corner at ([x], [y]) on the
 * canvas. A cel may extend past the canvas edge. [opacity] is `0..255`.
 */
data class AseCel(
    val layerIndex: Int,
    val image: IndexedRaster,
    val x: Int = 0,
    val y: Int = 0,
    val opacity: Int = Argb.OPAQUE_ALPHA,
) {
    init {
        require(opacity in 0..Argb.OPAQUE_ALPHA) { "Cel opacity out of range: $opacity" }
        require(x in Short.MIN_VALUE..Short.MAX_VALUE && y in Short.MIN_VALUE..Short.MAX_VALUE) {
            "Cel position out of range: ($x, $y)"
        }
        require(image.width <= AseLimits.MAX_DIMENSION && image.height <= AseLimits.MAX_DIMENSION) {
            "Cel image too large: ${image.width}x${image.height}"
        }
    }
}

/** Upper bounds the format's 16-bit fields put on a sprite. */
internal object AseLimits {
    const val MAX_DIMENSION = 0xFFFF
    const val MAX_COUNT = 0xFFFF
    const val MAX_PALETTE_SIZE = 256
}
