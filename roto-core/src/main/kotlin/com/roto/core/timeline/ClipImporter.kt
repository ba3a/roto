package com.roto.core.timeline

import com.roto.core.cache.StageCache
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import java.nio.file.Path

/**
 * Saves a trimmed video, or a photo with a photo of the empty wall, as a clip of the profile
 * [profileId]: the files join the profile's sources, the clip joins `profile.json`, and its frames are
 * extracted at the working height, so a bad range or a file `ffmpeg` cannot decode shows up now rather
 * than at the first render.
 *
 * A clip already filmed for that action and facing is [recut][Clip.recut] or [reshot][Clip.reshot],
 * keeping its settings.
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

    /** Saves the photo [file] as a still against the photo of the empty [wall], which must line up with it. */
    fun saveStill(file: Path, wall: Path, slot: Slot): Clip {
        val folder = repository.folder(profileId)
        val profile = repository.load(profileId)
        requireLinedUp(file, wall)
        val source = SourceImport.import(file, folder)
        val plate = SourceImport.import(wall, folder)
        val clip = profile.clip(slot.action, slot.facing)?.reshot(source, plate)
            ?: Clip.still(slot.action, slot.facing, source, plate)
        repository.save(profileId, profile.withClip(clip))
        return clip
    }

    /** The photo of the empty [wall] brought into the sources as [clip]'s plate, which must line up with it. */
    fun wallPhoto(clip: Clip, wall: Path): PlateSource.Photo {
        val folder = repository.folder(profileId)
        requireLinedUp(folder.sourceFile(clip), wall)
        return PlateSource.Photo(SourceImport.import(wall, folder))
    }

    private fun requireLinedUp(photo: Path, wall: Path) {
        val extractor = FrameExtractor(ffmpeg, StageCache(repository.folder(profileId).cacheFolder))
        val figure = extractor.still(photo).get().single()
        val plate = extractor.still(wall).get().single()
        require(figure.width == plate.width && figure.height == plate.height) {
            "The wall photo comes out ${plate.width}x${plate.height} and the photo ${figure.width}x${figure.height}: " +
                "take both the same way up, at the same size"
        }
    }
}
