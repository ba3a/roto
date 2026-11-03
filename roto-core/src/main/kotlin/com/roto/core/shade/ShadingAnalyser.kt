package com.roto.core.shade

import com.roto.core.cache.CacheKey
import com.roto.core.cache.JsonCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The shading stage: a clip's [ClipShading] by [ShadingFit], over every frame, cached under the
 * frames, the zone maps, the number of steps in each zone's ramp and the [ShadeSettings]. Moving a
 * key-frame marker does not refit it: the thresholds belong to the whole clip.
 */
class ShadingAnalyser(private val cache: StageCache) {

    /**
     * The [ClipShading] of a clip's [frames] and [zones], where [steps] counts the steps of each zone's
     * ramp.
     */
    fun shading(
        frames: StageOutput<List<Raster>>,
        zones: StageOutput<List<IndexedRaster>>,
        steps: List<Int>,
        settings: ShadeSettings,
    ): StageOutput<ClipShading> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("frames", frames.key)
            put("zones", zones.key)
            put("steps", steps, ListSerializer(Int.serializer()))
            put("settings", settings, ShadeSettings.serializer())
        }
        return StageOutput(key) { cache.get(key, CODEC) { ShadingFit.fit(frames.get(), zones.get(), steps, settings) } }
    }

    /** The stage name and its algorithm version. */
    companion object {
        /** The stage name, which is the cache subfolder its outputs live in. */
        const val STAGE = "shading"
        private const val VERSION = 1
        private val CODEC = JsonCacheCodec(ClipShading.serializer())
    }
}
