package com.roto.core.video

import com.roto.core.cache.CacheKey
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.image.Raster
import com.roto.core.profile.TimeRange
import java.nio.file.Path

/**
 * The first pipeline stage: a clip's trimmed range of its source video, as frames at a working
 * height. The frames are cached under the source's content, the range, the frame rate, the height and
 * the `ffmpeg` version, so trimming again or upgrading `ffmpeg` extracts again and nothing else does.
 */
class FrameExtractor(private val ffmpeg: Ffmpeg, private val cache: StageCache) {

    /** [range] of [source] as frames at an even [frameRate], each [height] pixels tall. */
    fun extract(source: Path, range: TimeRange, frameRate: FrameRate, height: Int = WORKING_HEIGHT): List<Raster> =
        cache.getOrFill(key(source, range, frameRate, height), RasterSequenceCacheCodec::read) { entry ->
            ffmpeg.extractRange(source, range, frameRate, height, entry)
        }

    fun key(source: Path, range: TimeRange, frameRate: FrameRate, height: Int = WORKING_HEIGHT): CacheKey =
        CacheKey.of(STAGE, VERSION) {
            putFile("source", source)
            put("range", range, TimeRange.serializer())
            put("frameRate", frameRate.toString())
            put("height", height)
            put("ffmpeg", ffmpeg.version)
        }

    companion object {
        const val STAGE = "frames"
        private const val VERSION = 1

        /** Tall enough to mask and track a figure well, small enough to keep every later stage quick. */
        const val WORKING_HEIGHT = 480
    }
}
