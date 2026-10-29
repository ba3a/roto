package com.roto.core.framebox

import com.roto.core.cache.CacheKey
import com.roto.core.cache.JsonCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import com.roto.core.project.Canvas
import com.roto.core.track.RootTrack

/**
 * The frame box stage: a clip's [FrameBoxes] by the [FrameBoxLayout], cached under the masks (for
 * the ground line), the root track, the profile's [ScaleSettings] and the project's [Canvas]. Moving
 * the scale marks or resizing the canvas lays the boxes out again and touches nothing upstream.
 */
class FrameBoxer(private val cache: StageCache) {

    fun boxes(
        masks: StageOutput<List<Mask>>,
        track: StageOutput<RootTrack>,
        scale: ScaleSettings,
        canvas: Canvas,
    ): StageOutput<FrameBoxes> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("masks", masks.key)
            put("track", track.key)
            put("scale", scale, ScaleSettings.serializer())
            put("canvas", canvas, Canvas.serializer())
        }
        return StageOutput(key) { cache.get(key, CODEC) { FrameBoxLayout.of(masks.get(), track.get(), scale, canvas) } }
    }

    companion object {
        const val STAGE = "framebox"
        private const val VERSION = 1
        private val CODEC = JsonCacheCodec(FrameBoxes.serializer())
    }
}
