package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.image.Raster
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.project.ProjectConfig
import com.roto.core.timeline.ClipImporter
import com.roto.core.timeline.FilmstripBuilder
import com.roto.core.timeline.Trim
import com.roto.core.video.Ffmpeg
import com.roto.core.video.VideoInfo
import java.nio.file.Path
import kotlin.math.abs

/** What filing new footage in [existing]'s place costs it. */
internal fun replacementWarning(existing: Clip): String =
    if (existing.hasFrameWork) {
        "${existing.id} is already filmed: saving replaces its footage and clears its key-frame " +
            "markers, mask fixes and zone samples"
    } else {
        "${existing.id} is already filmed: saving replaces its footage"
    }

/** The three things the filmstrip lets the artist drag. */
enum class Grip { START, END, PLAYHEAD }

/** A video being trimmed: the [file] and what `ffprobe` said of it. */
class TrimVideo(val file: Path, val info: VideoInfo)

/** The clip screens' way in: the profile [profileId] of [repository], decoded with [ffmpeg]. */
class ClipTarget(val ffmpeg: Ffmpeg, val repository: ProfileRepository, val profileId: String)

/**
 * What the trim screen shows: the [range] between the handles, the [playheadMs] the preview shows,
 * the [action] and [facing] the clip is filed under, the [strip] and [preview] once decoded, whether
 * it is [saving], and a [message] about the last thing that went wrong.
 */
data class TrimState(
    val range: TimeRange,
    val playheadMs: Long,
    val action: String,
    val facing: String,
    val strip: List<Raster>? = null,
    val preview: Raster? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * The trim screen's state and what can be done to it. A trim is either of a new video, which starts
 * on the whole of it and may be filed under any action and facing, or of the [editing] clip, whose
 * place stays fixed. Decoding and saving run off the UI thread; every rule is `roto-core`'s.
 */
class TrimModel(
    val video: TrimVideo,
    private val target: ClipTarget,
    private val config: ProjectConfig,
    private val profile: Profile,
    val editing: Clip? = null,
) : StripGestures {
    private val trim = Trim(video.info)
    private var grip = Grip.PLAYHEAD

    var state by mutableStateOf(initialState())
        private set

    val actions: List<String> get() = config.actions
    val facings: List<String> get() = config.facings

    /** How many frames the range holds. */
    val frameCount: Int get() = trim.frameCount(state.range)

    /** What saving will cost the clip already in this place, if anything. */
    val warning: String?
        get() {
            val existing = editing ?: profile.clip(state.action, state.facing) ?: return null
            return when {
                editing == null -> replacementWarning(existing)
                state.range != existing.trim && existing.hasFrameWork ->
                    "Changing the trim clears this clip's key-frame markers, mask fixes and zone samples"
                else -> null
            }
        }

    /** Takes hold of whichever handle is within [reach] of [at], or else the playhead, and moves it there. */
    override fun press(at: Long, reach: Long) {
        val range = state.range
        val toStart = abs(at - range.startMs)
        val toEnd = abs(at - range.endMs)
        grip = when {
            minOf(toStart, toEnd) > reach -> Grip.PLAYHEAD
            toStart <= toEnd -> Grip.START
            else -> Grip.END
        }
        drag(at)
    }

    /** Moves what [press] took hold of to [at]. The preview follows it, so the artist sees where it lands. */
    override fun drag(at: Long) {
        state = when (grip) {
            Grip.START -> trim.withStart(state.range, at).let { state.copy(range = it, playheadMs = it.startMs) }
            Grip.END -> trim.withEnd(state.range, at).let { state.copy(range = it, playheadMs = trim.lastFrame(it)) }
            Grip.PLAYHEAD -> state.copy(playheadMs = trim.snap(at))
        }
    }

    fun chooseAction(action: String) {
        if (editing == null) state = state.copy(action = action)
    }

    fun chooseFacing(facing: String) {
        if (editing == null) state = state.copy(facing = facing)
    }

    /** Decodes the filmstrip; cached, so a video seen before takes no time. */
    suspend fun loadStrip() {
        val strip = decoding {
            val cache = StageCache(target.repository.folder(target.profileId).cacheFolder)
            FilmstripBuilder(target.ffmpeg, cache).strip(video.file, video.info.durationMs).get()
        }
        if (strip != null) state = state.copy(strip = strip)
    }

    /** Decodes the frame under the playhead. */
    suspend fun loadPreview() {
        val playhead = state.playheadMs
        val preview = decoding { target.ffmpeg.thumbnail(video.file, playhead, PREVIEW_HEIGHT) }
        if (preview != null) state = state.copy(preview = preview)
    }

    /** Saves the clip: true once it is in the profile and its frames are extracted. */
    suspend fun save(): Boolean {
        state = state.copy(saving = true, message = null)
        val slot = ClipImporter.Slot(state.action, state.facing)
        val range = state.range
        val clip = decoding {
            ClipImporter(target.ffmpeg, target.repository, target.profileId).save(video.file, slot, range)
        }
        state = state.copy(saving = false)
        return clip != null
    }

    private suspend fun <T : Any> decoding(work: () -> T): T? =
        offUiThread({ state = state.copy(message = it) }, work)

    private fun initialState(): TrimState {
        val range = editing?.trim ?: trim.whole
        return TrimState(
            range = range,
            playheadMs = range.startMs,
            action = editing?.action ?: config.actions.first(),
            facing = editing?.facing ?: config.facings.first(),
        )
    }

    private companion object {
        const val PREVIEW_HEIGHT = 480
    }
}
