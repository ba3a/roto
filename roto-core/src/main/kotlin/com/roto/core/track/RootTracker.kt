package com.roto.core.track

import com.roto.core.cache.CacheKey
import com.roto.core.cache.JsonCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask

/**
 * The root tracking stage: a clip's [RootTrack] from its masks, by the [Stabiliser]. Cached under
 * the masks and the [RootSettings], so retuning the hip band or the smoothing touches nothing
 * upstream.
 */
class RootTracker(private val cache: StageCache) {

    /** The [RootTrack] of a clip's [masks] under [settings]. */
    fun track(masks: StageOutput<List<Mask>>, settings: RootSettings): StageOutput<RootTrack> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("masks", masks.key)
            put("settings", settings, RootSettings.serializer())
        }
        return StageOutput(key) { cache.get(key, CODEC) { Stabiliser.track(masks.get(), settings) } }
    }

    /** The stage name and its algorithm version. */
    companion object {
        /** The stage name, which is the cache subfolder its outputs live in. */
        const val STAGE = "root"
        private const val VERSION = 2
        private val CODEC = JsonCacheCodec(RootTrack.serializer())
    }
}
