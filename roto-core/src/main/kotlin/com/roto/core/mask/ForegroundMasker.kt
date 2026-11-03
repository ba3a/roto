package com.roto.core.mask

import com.roto.core.cache.CacheKey
import com.roto.core.cache.MaskSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import com.roto.core.image.Raster

/**
 * The mask stage: one [ForegroundMask] per frame of a clip, against the clip's clean plate. Cached
 * under the frames, the plate and the [MaskSettings], so changing a threshold masks again and
 * nothing upstream is touched.
 */
class ForegroundMasker(private val cache: StageCache) {

    /** The masks of [frames] against [plate], found with [settings]. */
    fun masks(
        frames: StageOutput<List<Raster>>,
        plate: StageOutput<Raster>,
        settings: MaskSettings,
    ): StageOutput<List<Mask>> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("frames", frames.key)
            put("plate", plate.key)
            put("settings", settings, MaskSettings.serializer())
        }
        return StageOutput(key) {
            cache.get(key, MaskSequenceCacheCodec) {
                val mask = ForegroundMask(plate.get(), settings)
                frames.get().map { mask.of(it) }
            }
        }
    }

    /** The stage name and its algorithm version. */
    companion object {
        /** The stage name, which is the cache subfolder its outputs live in. */
        const val STAGE = "mask"
        private const val VERSION = 1
    }
}
