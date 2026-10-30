package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Raster
import com.roto.core.plate.PlateFootage
import com.roto.core.profile.Clip
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.TimeRange
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import com.roto.core.video.FrameRate
import com.roto.core.video.VideoInfo

/**
 * The [frames] of one [clip] of the profile in [folder]: a cut of its video at the source's own frame
 * rate, which `ffprobe` reports, or a still's one frame. They are held once here, so everything looking
 * at one clip shares them: a clip's frames run to hundreds of megabytes, and a screen that masks them
 * under each new setting must not load them again each time.
 */
class ClipFootage(
    ffmpeg: Ffmpeg,
    cache: StageCache,
    private val folder: ProfileFolder,
    private val clip: Clip,
) : PlateFootage {

    private val extractor = FrameExtractor(ffmpeg, cache)
    private val source = folder.sourceFile(clip)

    /** What `ffprobe` says of the source video; a still has none. */
    val video: VideoInfo by lazy { ffmpeg.probe(source) }

    val frameRate: FrameRate get() = if (clip.isStill) STILL_RATE else video.frameRate

    override val frames: StageOutput<List<Raster>> by lazy {
        clip.trim?.let { extractor.frames(source, it, frameRate) } ?: extractor.still(source)
    }

    /** Another [range] of the same video, lined up with [frames] pixel for pixel: an empty-wall range. */
    override fun range(range: TimeRange): StageOutput<List<Raster>> = extractor.frames(source, range, frameRate)

    override fun photo(source: String): StageOutput<List<Raster>> = extractor.still(folder.sourceFile(source))

    private companion object {
        /** Nothing moves in a still, but a frame needs a duration: a second is easy to see in an editor. */
        val STILL_RATE = FrameRate(1, 1)
    }
}
