package com.roto.core.video

import com.roto.core.cache.CacheKey
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.profile.TimeRange
import java.nio.file.Path

/**
 * The first pipeline stage: a range of a source video, or a photo, as frames at a working height. The
 * frames are cached under the source's content, the range and frame rate if any, the height and the
 * `ffmpeg` version, so trimming again or upgrading `ffmpeg` extracts again and nothing else does.
 */
class FrameExtractor(private val ffmpeg: Ffmpeg, private val cache: StageCache) {

    /** [range] of [source] as frames at an even [frameRate], each [height] pixels tall. */
    fun frames(
        source: Path,
        range: TimeRange,
        frameRate: FrameRate,
        height: Int = WORKING_HEIGHT,
    ): StageOutput<List<Raster>> {
        val key = CacheKey.of(STAGE, VERSION) {
            putFile("source", source)
            put("range", range, TimeRange.serializer())
            put("frameRate", frameRate.toString())
            put("height", height)
            put("ffmpeg", ffmpeg.version)
        }
        return StageOutput(key) {
            cache.getOrFill(key, RasterSequenceCacheCodec::read) { entry ->
                ffmpeg.extractRange(source, range, frameRate, height, entry)
            }
        }
    }

    /**
     * The photo [source] as a single frame [height] pixels tall. `ffmpeg` decodes it as it does a
     * video, upright and through the same scaling, so everything after this stage is the same.
     */
    fun still(source: Path, height: Int = WORKING_HEIGHT): StageOutput<List<Raster>> {
        val key = CacheKey.of(STILL_STAGE, VERSION) {
            putFile("source", source)
            put("height", height)
            put("ffmpeg", ffmpeg.version)
        }
        return StageOutput(key) {
            cache.get(key, RasterSequenceCacheCodec) { listOf(ffmpeg.photo(source, height)) }
        }
    }

    companion object {
        const val STAGE = "frames"
        const val STILL_STAGE = "still"
        private const val VERSION = 1

        /** Tall enough to mask and track a figure well, small enough to keep every later stage quick. */
        const val WORKING_HEIGHT = 480
    }
}
