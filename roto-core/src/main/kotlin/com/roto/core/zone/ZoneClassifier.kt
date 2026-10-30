package com.roto.core.zone

import com.roto.core.cache.CacheKey
import com.roto.core.cache.LabelSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Mask
import com.roto.core.image.Raster

/**
 * The zone stage: one label map per frame of a clip by [NearestZone], with the artist's strokes on
 * top by [ZoneBrush], cached under the frames, the masks and the [ZoneSettings]. Adding a sample or retuning the weight labels every frame again and
 * touches nothing upstream.
 */
class ZoneClassifier(private val cache: StageCache) {

    fun zones(
        frames: StageOutput<List<Raster>>,
        masks: StageOutput<List<Mask>>,
        settings: ZoneSettings,
    ): StageOutput<List<IndexedRaster>> {
        val key = CacheKey.of(STAGE, VERSION) {
            put("frames", frames.key)
            put("masks", masks.key)
            put("settings", settings, ZoneSettings.serializer())
        }
        return StageOutput(key) {
            cache.get(key, LabelSequenceCacheCodec) {
                val clipFrames = frames.get()
                val clipMasks = masks.get()
                require(clipFrames.size == clipMasks.size) { "${clipFrames.size} frames but ${clipMasks.size} masks" }
                settings.fixes.firstOrNull { it.frame !in clipFrames.indices }?.let { fix ->
                    throw IllegalArgumentException(
                        "A zone fix is on frame ${fix.frame}, but the clip has ${clipFrames.size} frames",
                    )
                }
                val prototypes = NearestZone.prototypes(clipFrames, settings)
                val fixes = settings.fixes.groupBy { it.frame }
                clipFrames.indices.map { frame ->
                    val labels =
                        NearestZone.label(clipFrames[frame], clipMasks[frame], prototypes, settings.lightnessWeight)
                    ZoneBrush.apply(labels, fixes[frame].orEmpty(), settings)
                }
            }
        }
    }

    companion object {
        const val STAGE = "zone"
        private const val VERSION = 1
    }
}
