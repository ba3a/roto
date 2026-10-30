package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.mask.BrushMode
import com.roto.core.mask.BrushPoint
import com.roto.core.mask.BrushStroke
import com.roto.core.mask.ForegroundMask
import com.roto.core.mask.MaskBrush
import com.roto.core.mask.MaskSettings
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.TimeRange
import com.roto.core.render.ClipFootage
import com.roto.core.render.ClipMasking
import com.roto.core.timeline.Trim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path

/** What the setup preview shows: the frame under its mask, the bare frame, or the clean plate. */
enum class SetupView { MASK, FRAME, PLATE }

/** The brush the artist holds. */
data class Brush(val mode: BrushMode = BrushMode.ADD, val radius: Int = 6)

/**
 * What the clip setup screen shows: the [clip] as edited, the [frame] being looked at of [frameCount],
 * its [picture], [plate] and [mask] once computed, the [keyFrames] brush fixes go on, the [brush] and
 * any [stroke] in progress, and a [message] about the last thing that went wrong.
 */
data class SetupState(
    val clip: Clip,
    val frame: Int = 0,
    val frameCount: Int = 0,
    val view: SetupView = SetupView.MASK,
    val picture: Raster? = null,
    val plate: Raster? = null,
    val mask: Mask? = null,
    val keyFrames: List<Int>? = null,
    val brush: Brush = Brush(),
    val stroke: BrushStroke? = null,
    val message: String? = null,
) {
    /** Brush fixes go on key frames only: the frames that get drawn. */
    val canBrush: Boolean get() = keyFrames?.contains(frame) == true

    /** The strokes already on the frame being looked at. */
    val frameFixes: List<BrushStroke> get() = clip.fixes.filter { it.frame == frame }
}

/**
 * The clip setup screen's state and what can be done to it: choose the plate, tune the mask, and fix
 * key frames by brush. The preview is one frame masked directly, so a slider answers at once; key
 * frames come from the stages, over the whole clip. Work runs off the UI thread, and every rule is
 * `roto-core`'s.
 */
class ClipSetupModel(clip: Clip, source: Path, private val target: ClipTarget) {

    private val cache = StageCache(target.repository.folder(target.profileId).cacheFolder)
    private val footage = ClipFootage(target.ffmpeg, cache, source, clip.trim)
    private val previewing = Mutex()
    private val findingKeyFrames = Mutex()
    private var opened = false
    private var plate: Pair<PlateSource, Raster>? = null
    private var unfixedMask: Mask? = null

    var state by mutableStateOf(SetupState(clip))
        private set

    /** The source's length, once [open] has read it. */
    val videoDurationMs: Long get() = if (opened) footage.video.durationMs else 0

    /** Reads the clip's frames, which stay in memory, once, while the screen is open. */
    suspend fun open() {
        val frames = working {
            footage.video
            footage.frames.get()
        } ?: return
        opened = true
        state = state.copy(frameCount = frames.size)
    }

    /** Masks the frame being looked at with the settings as they stand. */
    suspend fun refresh() {
        if (!opened) return
        val clip = state.clip
        val frame = state.frame
        val preview = previewing.withLock {
            working {
                val picture = footage.frames.get()[frame]
                val background = plateOf(clip)
                Preview(picture, background, ForegroundMask(background, clip.mask).of(picture))
            }
        } ?: return
        unfixedMask = preview.mask
        state = state.copy(picture = preview.picture, plate = preview.plate, message = null)
        showFixes()
    }

    /**
     * Runs the stages as far as the key frames, for the settings as they stand. The plate comes first,
     * through the preview's own hold on it, so the two never build the same plate at once.
     */
    suspend fun loadKeyFrames() {
        val clip = state.clip
        previewing.withLock { working { plateOf(clip) } } ?: return
        val keyFrames = findingKeyFrames.withLock { working { masking(clip).keyFrames.frames } }
        if (keyFrames != null) state = state.copy(keyFrames = keyFrames)
    }

    /** Writes the clip as edited into `profile.json`. */
    suspend fun save() {
        val clip = state.clip
        working {
            val repository = target.repository
            repository.save(target.profileId, repository.load(target.profileId).withClip(clip))
        }
    }

    fun selectFrame(frame: Int) {
        state = state.copy(frame = frame.coerceIn(0, (state.frameCount - 1).coerceAtLeast(0)), stroke = null)
    }

    fun selectView(view: SetupView) {
        state = state.copy(view = view)
    }

    fun useClipMedian() = edit { it.copy(plate = PlateSource.ClipMedian) }

    /** Marks an empty-wall range for the plate: the start of the video until the artist moves it. */
    fun useEmptyRange() {
        if (!opened) return
        val trim = Trim(footage.video)
        edit { it.copy(plate = PlateSource.EmptyRange(trim.withEnd(trim.whole, FIRST_RANGE_MS))) }
    }

    fun moveEmptyStart(ms: Long) = moveEmptyRange { trim, range -> trim.withStart(range, ms) }

    fun moveEmptyEnd(ms: Long) = moveEmptyRange { trim, range -> trim.withEnd(range, ms) }

    fun changeMask(change: (MaskSettings) -> MaskSettings) = edit { it.copy(mask = change(it.mask)) }

    fun changeBrush(brush: Brush) {
        state = state.copy(brush = brush)
    }

    /** Starts a stroke at ([x], [y]) in working-frame pixels, on a key frame with its mask showing. */
    fun beginStroke(x: Int, y: Int) {
        if (!state.canBrush || state.view != SetupView.MASK) return
        val brush = state.brush
        state = state.copy(stroke = BrushStroke(state.frame, brush.mode, brush.radius, listOf(point(x, y))))
        showFixes()
    }

    fun extendStroke(x: Int, y: Int) {
        val stroke = state.stroke ?: return
        state = state.copy(stroke = stroke.copy(points = stroke.points + point(x, y)))
        showFixes()
    }

    fun endStroke() {
        val stroke = state.stroke ?: return
        edit { it.copy(fixes = it.fixes + stroke) }
        state = state.copy(stroke = null)
    }

    /** Takes back the last stroke on the frame being looked at. */
    fun undoStroke() {
        val last = state.frameFixes.lastOrNull() ?: return
        val index = state.clip.fixes.lastIndexOf(last)
        edit { clip -> clip.copy(fixes = clip.fixes.filterIndexed { position, _ -> position != index }) }
        showFixes()
    }

    /** Takes back every stroke on the frame being looked at. */
    fun clearFrame() {
        val frame = state.frame
        edit { clip -> clip.copy(fixes = clip.fixes.filter { it.frame != frame }) }
        showFixes()
    }

    private fun edit(change: (Clip) -> Clip) {
        state = state.copy(clip = change(state.clip))
    }

    private fun moveEmptyRange(move: (Trim, TimeRange) -> TimeRange) {
        if (!opened) return
        val marked = state.clip.plate as? PlateSource.EmptyRange ?: return
        edit { it.copy(plate = PlateSource.EmptyRange(move(Trim(footage.video), marked.range))) }
    }

    private fun point(x: Int, y: Int) = BrushPoint(x.coerceAtLeast(0), y.coerceAtLeast(0))

    private fun showFixes() {
        val mask = unfixedMask ?: return
        state = state.copy(mask = MaskBrush.apply(mask, state.frameFixes + listOfNotNull(state.stroke)))
    }

    private fun masking(clip: Clip) = ClipMasking(footage, cache, clip)

    /** The plate for [clip]'s plate source, kept while it stays the same: a slider should not rebuild it. */
    private fun plateOf(clip: Clip): Raster {
        val kept = plate?.takeIf { it.first == clip.plate }
        return kept?.second ?: masking(clip).plate.get().also { plate = clip.plate to it }
    }

    private suspend fun <T : Any> working(work: () -> T): T? =
        try {
            withContext(Dispatchers.IO) { work() }
        } catch (e: IOException) {
            state = state.copy(message = e.message)
            null
        } catch (e: IllegalArgumentException) {
            state = state.copy(message = e.message)
            null
        }

    private class Preview(val picture: Raster, val plate: Raster, val mask: Mask)

    private companion object {
        /** A first guess at the empty wall: the spec's filming guide records it first. */
        const val FIRST_RANGE_MS = 1000L
    }
}
