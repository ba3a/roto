package com.roto.core.plate

import com.roto.core.cache.CacheKey
import com.roto.core.cache.RasterCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.profile.PlateSource
import com.roto.core.profile.TimeRange

/**
 * The clean plate stage: the empty background a clip's figure is masked against, as the
 * [TemporalMedian] of either a marked empty-wall range or the clip's own frames.
 *
 * The plate is keyed by the frames it is the median of, so it is cached once per distinct set of
 * frames: two clips cut from one video with the same empty range share one plate.
 */
class CleanPlateBuilder(private val cache: StageCache) {

    /**
     * The plate [source] describes for a clip whose frames are [clipFrames]. An empty range is
     * extracted through [rangeFrames], which must take it from the clip's source at the clip's frame
     * rate and working height, so the plate lines up with every frame pixel for pixel.
     */
    fun plate(
        source: PlateSource,
        clipFrames: StageOutput<List<Raster>>,
        rangeFrames: (TimeRange) -> StageOutput<List<Raster>>,
    ): StageOutput<Raster> {
        val frames = when (source) {
            is PlateSource.EmptyRange -> rangeFrames(source.range)
            PlateSource.ClipMedian -> clipFrames
        }
        val key = CacheKey.of(STAGE, VERSION) { put("frames", frames.key) }
        return StageOutput(key) { cache.get(key, RasterCacheCodec) { TemporalMedian.of(frames.get()) } }
    }

    companion object {
        const val STAGE = "plate"
        private const val VERSION = 1
    }
}
