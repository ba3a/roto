package com.roto.core.plate

import com.roto.core.cache.CacheKey
import com.roto.core.cache.RasterCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.profile.PlateSource
import com.roto.core.profile.TimeRange

/**
 * The frames a clip's plate can come from. All of them must be at the clip's working height, and a
 * range at its frame rate, so the plate lines up with every frame pixel for pixel.
 */
interface PlateFootage {

    /** The clip's own frames. */
    val frames: StageOutput<List<Raster>>

    /** Another [range] of the clip's source video. */
    fun range(range: TimeRange): StageOutput<List<Raster>>

    /** The photo [source] among the profile's sources, as one frame. */
    fun photo(source: String): StageOutput<List<Raster>>
}

/**
 * The clean plate stage: the empty background a clip's figure is masked against, as the
 * [TemporalMedian] of a marked empty-wall range, a photo of the wall, or the clip's own frames.
 *
 * The plate is keyed by the frames it is the median of, so it is cached once per distinct set of
 * frames: two clips cut from one video with the same empty range share one plate.
 */
class CleanPlateBuilder(private val cache: StageCache) {

    /** The plate [source] describes for a clip whose frames are in [footage]. */
    fun plate(source: PlateSource, footage: PlateFootage): StageOutput<Raster> {
        val frames = when (source) {
            is PlateSource.EmptyRange -> footage.range(source.range)
            is PlateSource.Photo -> footage.photo(source.source)
            PlateSource.ClipMedian -> footage.frames
        }
        val key = CacheKey.of(STAGE, VERSION) { put("frames", frames.key) }
        return StageOutput(key) { cache.get(key, RasterCacheCodec) { TemporalMedian.of(frames.get()) } }
    }

    companion object {
        const val STAGE = "plate"
        private const val VERSION = 1
    }
}
