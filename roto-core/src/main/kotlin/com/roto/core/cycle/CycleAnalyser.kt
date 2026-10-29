package com.roto.core.cycle

import com.roto.core.cache.CacheKey
import com.roto.core.cache.JsonCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import com.roto.core.track.RootTrack
import com.roto.core.video.FrameRate

/**
 * The cycle stage: a clip's [CycleAnalysis] by the [CycleDetector], cached under the masks, the root
 * track, the frame rate, the [CycleSettings] and whether the clip loops. Only a proposal: the
 * artist's own markers, once set, take its place and are not touched by it.
 */
class CycleAnalyser(private val cache: StageCache) {

    fun analyse(
        masks: StageOutput<List<Mask>>,
        track: StageOutput<RootTrack>,
        frameRate: FrameRate,
        settings: CycleSettings,
        loop: Boolean,
    ): StageOutput<CycleAnalysis> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("masks", masks.key)
            put("track", track.key)
            put("frameRate", frameRate.toString())
            put("settings", settings, CycleSettings.serializer())
            put("loop", loop)
        }
        return StageOutput(key) {
            cache.get(key, CODEC) { CycleDetector.analyse(masks.get(), track.get(), frameRate, settings, loop) }
        }
    }

    companion object {
        const val STAGE = "cycle"
        private const val VERSION = 1
        private val CODEC = JsonCacheCodec(CycleAnalysis.serializer())
    }
}
