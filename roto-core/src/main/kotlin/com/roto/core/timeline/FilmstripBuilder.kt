package com.roto.core.timeline

import com.roto.core.cache.CacheKey
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.video.Ffmpeg
import java.nio.file.Path

/**
 * The filmstrip stage: evenly spread thumbnails of a whole video, for trimming it. Cached under the
 * source's content, the count, the height and the `ffmpeg` version, so opening a video again decodes
 * nothing.
 */
class FilmstripBuilder(private val ffmpeg: Ffmpeg, private val cache: StageCache) {

    /** [count] thumbnails of [source], which lasts [durationMs], each [height] pixels tall. */
    fun strip(
        source: Path,
        durationMs: Long,
        count: Int = FRAMES,
        height: Int = THUMBNAIL_HEIGHT,
    ): StageOutput<List<Raster>> {
        val key = CacheKey.of(STAGE, VERSION) {
            putFile("source", source)
            put("durationMs", durationMs)
            put("count", count)
            put("height", height)
            put("ffmpeg", ffmpeg.version)
        }
        return StageOutput(key) {
            cache.get(key, RasterSequenceCacheCodec) {
                centres(durationMs, count).map { ffmpeg.thumbnail(source, it, height) }
            }
        }
    }

    companion object {
        const val STAGE = "filmstrip"
        private const val VERSION = 1

        /** Enough to find a moment in a few seconds of video; each one costs a run of `ffmpeg`. */
        const val FRAMES = 12

        const val THUMBNAIL_HEIGHT = 96

        /** The centres of [count] equal slices of [length]: of a video in ms, or of a clip in frames. */
        fun centres(length: Long, count: Int): List<Long> {
            require(length > 0) { "A filmstrip needs something to show, got a length of $length" }
            require(count > 0) { "A filmstrip needs at least one thumbnail, got $count" }
            return (0 until count).map { length * (2L * it + 1) / (2L * count) }
        }
    }
}
