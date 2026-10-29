package com.roto.core.shade

import com.roto.core.cache.CacheKey
import com.roto.core.cache.LabelSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.KeyFrames
import com.roto.core.framebox.CanvasSampler
import com.roto.core.framebox.FrameBoxes
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The draft shading stage: each key frame, sampled onto the canvas at [FACTOR] times its size and
 * shaded by [RampShader], as palette labels. The draft is drawn large and reduced afterwards, so
 * the reduction can vote on what every final pixel shows.
 *
 * Cached under the frames, zone maps, boxes, shading, the key frames and each zone's colours, so
 * moving a marker or editing `ramps.json` shades again without refitting or relabelling anything.
 */
class DraftShader(private val cache: StageCache) {

    /** The inputs a draft is shaded from: all of one clip's. */
    class Inputs(
        val frames: StageOutput<List<Raster>>,
        val zones: StageOutput<List<IndexedRaster>>,
        val boxes: StageOutput<FrameBoxes>,
        val shading: StageOutput<ClipShading>,
    )

    fun shade(inputs: Inputs, keyFrames: KeyFrames, zoneColours: List<List<Int>>): StageOutput<List<IndexedRaster>> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("frames", inputs.frames.key)
            put("zones", inputs.zones.key)
            put("boxes", inputs.boxes.key)
            put("shading", inputs.shading.key)
            put("keyFrames", keyFrames.boundaries, ListSerializer(Int.serializer()))
            put("zoneColours", zoneColours, ListSerializer(ListSerializer(Int.serializer())))
            put("factor", FACTOR)
        }
        return StageOutput(key) {
            cache.get(key, LabelSequenceCacheCodec) {
                val frames = inputs.frames.get()
                val zones = inputs.zones.get()
                val boxes = inputs.boxes.get()
                val shader = RampShader(inputs.shading.get(), zoneColours)
                keyFrames.frames.map { frame ->
                    require(frame in frames.indices) { "Key frame $frame is beyond the clip's ${frames.size} frames" }
                    shader.shade(
                        CanvasSampler.sample(frames[frame], boxes, frame, FACTOR),
                        CanvasSampler.sample(zones[frame], boxes, frame, FACTOR),
                    )
                }
            }
        }
    }

    companion object {
        const val STAGE = "draft-shade"
        private const val VERSION = 1

        /** How many times the canvas size a draft is shaded at before it is reduced. */
        const val FACTOR = 4
    }
}
