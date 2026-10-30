package com.roto.core.timeline

import com.roto.core.cache.StageCache
import com.roto.core.profile.Clip
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import java.nio.file.Path

/**
 * Saves a trimmed video as a clip of the profile [profileId]: the video joins the profile's sources,
 * the clip joins `profile.json`, and its frames are extracted at the working height, so a bad range
 * or a video `ffmpeg` cannot decode shows up now rather than at the first render.
 *
 * A clip already filmed for that action and facing is [recut][Clip.recut], keeping its settings.
 */
class ClipImporter(
    private val ffmpeg: Ffmpeg,
    private val repository: ProfileRepository,
    private val profileId: String,
) {
    /** The action and facing a clip is filed under, from the project's lists. */
    data class Slot(val action: String, val facing: String)

    fun save(file: Path, slot: Slot, trim: TimeRange): Clip {
        val folder = repository.folder(profileId)
        val profile = repository.load(profileId)
        val source = SourceImport.import(file, folder)
        val clip = profile.clip(slot.action, slot.facing)?.recut(source, trim)
            ?: Clip(slot.action, slot.facing, source, trim)
        repository.save(profileId, profile.withClip(clip))
        val video = folder.sourceFile(clip)
        FrameExtractor(ffmpeg, StageCache(folder.cacheFolder)).frames(video, trim, ffmpeg.probe(video).frameRate).get()
        return clip
    }
}
