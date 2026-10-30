package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.profile.TimeRange
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import com.roto.core.video.FrameRate
import com.roto.core.video.VideoInfo
import java.nio.file.Path

/**
 * The [frames] one cut of a [source] video gives, at the source's own frame rate, which `ffprobe`
 * reports. They are held once here, so everything looking at one clip shares them: a clip's frames
 * run to hundreds of megabytes, and a screen that masks them under each new setting must not load
 * them again each time.
 */
class ClipFootage(ffmpeg: Ffmpeg, cache: StageCache, private val source: Path, trim: TimeRange) {

    private val extractor = FrameExtractor(ffmpeg, cache)

    val video: VideoInfo by lazy { ffmpeg.probe(source) }

    val frameRate: FrameRate get() = video.frameRate

    val frames: StageOutput<List<Raster>> by lazy { extractor.frames(source, trim, frameRate) }

    /** Another [range] of the same video, lined up with [frames] pixel for pixel: an empty-wall range. */
    fun range(range: TimeRange): StageOutput<List<Raster>> = extractor.frames(source, range, frameRate)
}
