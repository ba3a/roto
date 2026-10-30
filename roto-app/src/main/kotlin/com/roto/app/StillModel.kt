package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.image.Raster
import com.roto.core.profile.Profile
import com.roto.core.project.ProjectConfig
import com.roto.core.timeline.ClipImporter
import com.roto.core.video.FrameExtractor
import java.nio.file.Path

/**
 * What the still screen shows: the [action] and [facing] the still is filed under, the [wall] photo
 * chosen, the [picture] and [wallPicture] once decoded, whether it is [saving], and a [message] about
 * the last thing that went wrong.
 */
data class StillState(
    val action: String,
    val facing: String,
    val wall: Path? = null,
    val picture: Raster? = null,
    val wallPicture: Raster? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * The still screen's state and what can be done to it: a [photo] has nothing to trim, so all it needs
 * is a photo of the empty wall and a place among the project's actions and facings. Decoding and saving
 * run off the UI thread; every rule is `roto-core`'s.
 */
class StillModel(
    val photo: Path,
    private val target: ClipTarget,
    private val config: ProjectConfig,
    private val profile: Profile,
) {
    private val extractor =
        FrameExtractor(target.ffmpeg, StageCache(target.repository.folder(target.profileId).cacheFolder))

    var state by mutableStateOf(StillState(config.actions.first(), config.facings.first()))
        private set

    val actions: List<String> get() = config.actions
    val facings: List<String> get() = config.facings

    /** What saving will cost the clip already in this place, if anything. */
    val warning: String? get() = profile.clip(state.action, state.facing)?.let(::replacementWarning)

    fun chooseAction(action: String) {
        state = state.copy(action = action)
    }

    fun chooseFacing(facing: String) {
        state = state.copy(facing = facing)
    }

    /** Decodes the photo; cached, so one seen before takes no time. */
    suspend fun load() {
        val picture = decoding { extractor.still(photo).get().single() }
        if (picture != null) state = state.copy(picture = picture)
    }

    /** Makes [file] the photo of the empty wall, once it decodes. */
    suspend fun chooseWall(file: Path) {
        val wall = decoding { extractor.still(file).get().single() }
        if (wall != null) state = state.copy(wall = file, wallPicture = wall, message = null)
    }

    /** Saves the still: true once it is in the profile. */
    suspend fun save(): Boolean {
        val wall = state.wall ?: return false
        state = state.copy(saving = true, message = null)
        val slot = ClipImporter.Slot(state.action, state.facing)
        val clip = decoding {
            ClipImporter(target.ffmpeg, target.repository, target.profileId).saveStill(photo, wall, slot)
        }
        state = state.copy(saving = false)
        return clip != null
    }

    private suspend fun <T : Any> decoding(work: () -> T): T? =
        offUiThread({ state = state.copy(message = it) }, work)
}
