package com.roto.core.handoff

import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.CanvasSampler
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.palette.Palette
import com.roto.core.palette.PaletteQuantiser
import com.roto.core.video.FrameRate

/**
 * What Roto writes into a clip's handoff: the [palette], the canvas size, and one [HandoffFrame] per
 * key frame. Everything is in palette labels — palette index plus one, `0` transparent — which are
 * the handoff's own palette indices.
 */
class HandoffRender(val palette: Palette, val width: Int, val height: Int, val frames: List<HandoffFrame>) {
    init {
        require(frames.isNotEmpty()) { "A handoff needs at least one key frame" }
        frames.forEachIndexed { index, frame ->
            listOf(frame.reference, frame.draft).forEach { image ->
                require(image.width == width && image.height == height) {
                    "Key frame $index is ${image.width}x${image.height}, the canvas ${width}x$height"
                }
                require(image.maxIndex() <= palette.colors.size) { "Key frame $index uses a label beyond the palette" }
            }
        }
    }

    /** One clip's stage outputs a handoff is made from: every frame, its boxes, and one draft per key frame. */
    class Sources(val frames: List<Raster>, val boxes: FrameBoxes, val drafts: List<IndexedRaster>)

    companion object {
        /**
         * The handoff of one clip: for each of its [keyFrames], the source frame sampled onto the
         * canvas and mapped to the [palette] as the reference, the finished draft as the draft, and
         * the key frame's duration at [frameRate].
         */
        fun of(palette: Palette, keyFrames: KeyFrames, frameRate: FrameRate, sources: Sources): HandoffRender {
            require(sources.drafts.size == keyFrames.frames.size) {
                "${sources.drafts.size} drafts for ${keyFrames.frames.size} key frames"
            }
            val durations = keyFrames.durationsMs(frameRate)
            val handoffFrames = keyFrames.frames.mapIndexed { index, frame ->
                val sampled = CanvasSampler.sample(sources.frames[frame], sources.boxes, frame, 1)
                val reference = PaletteQuantiser.labels(sampled, palette)
                HandoffFrame(Math.toIntExact(durations[index]), reference, sources.drafts[index])
            }
            return HandoffRender(palette, sources.boxes.canvas.width, sources.boxes.canvas.height, handoffFrames)
        }
    }
}

/** One key frame of a handoff: shown for [durationMs], with its [reference] and its [draft]. */
class HandoffFrame(val durationMs: Int, val reference: IndexedRaster, val draft: IndexedRaster) {
    init {
        require(durationMs >= 1) { "A key frame must last at least 1 ms, got $durationMs" }
    }
}
