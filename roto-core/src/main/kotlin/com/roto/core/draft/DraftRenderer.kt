package com.roto.core.draft

import com.roto.core.cache.CacheKey
import com.roto.core.cache.LabelSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.CanvasSampler
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.IndexedRaster
import com.roto.core.shade.DraftColours
import com.roto.core.shade.DraftShader
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The draft stage: each key frame's shaded draft finished at canvas size by the [DraftFinisher], as
 * palette labels — what the handoff's `draft` layer shows. Cached under the shaded drafts, the zone
 * maps, the boxes, the key frames, the [DraftColours] and the [DraftSettings].
 */
class DraftRenderer(private val cache: StageCache) {

    /** The upstream outputs a draft is finished from, all of one clip's. */
    class Inputs(
        val shaded: StageOutput<List<IndexedRaster>>,
        val zones: StageOutput<List<IndexedRaster>>,
        val boxes: StageOutput<FrameBoxes>,
    )

    fun render(
        inputs: Inputs,
        keyFrames: KeyFrames,
        colours: DraftColours,
        settings: DraftSettings,
    ): StageOutput<List<IndexedRaster>> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("shaded", inputs.shaded.key)
            put("zones", inputs.zones.key)
            put("boxes", inputs.boxes.key)
            put("keyFrames", keyFrames.boundaries, ListSerializer(Int.serializer()))
            put("colours", colours, DraftColours.serializer())
            put("settings", settings, DraftSettings.serializer())
        }
        return StageOutput(key) {
            cache.get(key, LabelSequenceCacheCodec) {
                val shaded = inputs.shaded.get()
                val zones = inputs.zones.get()
                val boxes = inputs.boxes.get()
                require(shaded.size == keyFrames.frames.size) {
                    "${shaded.size} shaded drafts for ${keyFrames.frames.size} key frames"
                }
                keyFrames.frames.mapIndexed { index, frame ->
                    val sampledZones = CanvasSampler.sample(zones[frame], boxes, frame, DraftShader.FACTOR)
                    DraftFinisher.finish(shaded[index], sampledZones, DraftShader.FACTOR, colours, settings).colours
                }
            }
        }
    }

    companion object {
        const val STAGE = "draft"
        private const val VERSION = 1
    }
}
