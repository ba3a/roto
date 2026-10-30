package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.cycle.KeyFrames
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
import com.roto.core.timeline.ClipImporter
import com.roto.core.timeline.FilmstripBuilder
import com.roto.core.timeline.Trim
import com.roto.core.video.FrameRate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import kotlin.math.abs

/** What the setup preview shows: the frame under its mask, the bare frame, or the clean plate. */
enum class SetupView { MASK, FRAME, PLATE }

/** The brush the artist holds. */
data class Brush(val mode: BrushMode = BrushMode.ADD, val radius: Int = 6)

/**
 * What the clip setup screen shows: the [clip] as edited, the [frame] being looked at of [frameCount]
 * and the [frameRate] they run at, the [strip] of thumbnails, the frame's [picture], [plate] and [mask]
 * once computed, the cycle stage's [proposal] of boundaries and the [marker] selected on the strip, the
 * [brush] and any [stroke] in progress, and a [message] about the last thing that went wrong.
 */
data class SetupState(
    val clip: Clip,
    val frame: Int = 0,
    val frameCount: Int = 0,
    val frameRate: FrameRate? = null,
    val view: SetupView = SetupView.MASK,
    val strip: List<Raster>? = null,
    val picture: Raster? = null,
    val plate: Raster? = null,
    val mask: Mask? = null,
    val proposal: List<Int>? = null,
    val marker: Int? = null,
    val brush: Brush = Brush(),
    val stroke: BrushStroke? = null,
    val message: String? = null,
) {
    /** The key frames as they stand: the artist's markers once set, otherwise the proposal. */
    val keyFrames: KeyFrames? get() = (clip.markers ?: proposal)?.let { KeyFrames(it) }

    /** Brush fixes go on key frames only: the frames that get drawn. */
    val canBrush: Boolean get() = keyFrames?.frames?.contains(frame) == true

    /** The strokes already on the frame being looked at. */
    val frameFixes: List<BrushStroke> get() = clip.fixes.filter { it.frame == frame }

    /** Strokes left on frames a marker has moved away from: kept, but drawn only if one comes back. */
    val strandedFixes: Int
        get() = keyFrames?.frames?.let { drawn -> clip.fixes.count { it.frame !in drawn } } ?: 0
}

/**
 * The clip setup screen's state and what can be done to it: choose the plate, tune the mask, set the
 * key-frame markers on the filmstrip, and fix key frames by brush; a still has its one key frame and
 * only a photo of the wall to choose. The preview is one frame masked directly, so a slider answers at
 * once; the proposal comes from the stages, over the whole clip. Work runs off the UI thread, and every
 * rule is `roto-core`'s.
 */
class ClipSetupModel(clip: Clip, private val target: ClipTarget) {

    private val folder = target.repository.folder(target.profileId)
    private val cache = StageCache(folder.cacheFolder)
    private val footage = ClipFootage(target.ffmpeg, cache, folder, clip)
    private val previewing = Mutex()
    private val findingProposal = Mutex()
    private var opened = false
    private var plate: Pair<PlateSource, Raster>? = null
    private var unfixedMask: Mask? = null

    var state by mutableStateOf(SetupState(clip))
        private set

    val markers = Markers()

    /** What the picture does under the pointer. */
    val painting = Painting()

    /** The source's length, once [open] has read it. */
    val videoDurationMs: Long get() = if (opened) footage.video.durationMs else 0

    /** Reads the clip's frames, which stay in memory, once, while the screen is open. */
    suspend fun open() {
        val frames = working {
            if (!state.clip.isStill) footage.video
            footage.frames.get()
        } ?: return
        opened = true
        val strip = FilmstripBuilder.centres(frames.size.toLong(), minOf(FilmstripBuilder.FRAMES, frames.size))
            .map { frames[it.toInt()] }
        state = state.copy(frameCount = frames.size, frameRate = footage.frameRate, strip = strip)
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
     * Runs the stages as far as the proposal, for the settings as they stand, even with markers set:
     * it is what the artist goes back to. The plate comes first, through the preview's own hold on it,
     * so the two never build the same plate at once.
     */
    suspend fun loadProposal() {
        val clip = state.clip
        previewing.withLock { working { plateOf(clip) } } ?: return
        val proposal = findingProposal.withLock { working { masking(clip).cycle.get().markers } }
        if (proposal != null) state = state.copy(proposal = proposal)
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

    /** Takes [file], a photo of the empty wall, as the plate, once it lines up with the clip. */
    suspend fun useWallPhoto(file: Path) {
        val clip = state.clip
        val plate = working {
            ClipImporter(target.ffmpeg, target.repository, target.profileId).wallPhoto(clip, file)
        } ?: return
        edit { it.copy(plate = plate) }
    }

    /** Marks an empty-wall range for the plate: the start of the video until the artist moves it. */
    fun useEmptyRange() {
        if (!opened) return
        val trim = Trim(footage.video)
        edit { it.copy(plate = PlateSource.EmptyRange(trim.withEnd(trim.whole, FIRST_RANGE_MS))) }
    }

    fun moveEmptyStart(ms: Long) = moveEmptyRange { trim, range -> trim.withStart(range, ms) }

    fun moveEmptyEnd(ms: Long) = moveEmptyRange { trim, range -> trim.withEnd(range, ms) }

    fun changeMask(change: (MaskSettings) -> MaskSettings) = edit { it.copy(mask = change(it.mask)) }

    fun setLoop(loop: Boolean) = edit { it.copy(loop = loop) }

    fun changeBrush(brush: Brush) {
        state = state.copy(brush = brush)
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

    /** The brush on the picture: strokes in working-frame pixels, on a key frame with its mask showing. */
    inner class Painting : PictureGestures {

        override fun press(x: Int, y: Int) {
            if (!state.canBrush || state.view != SetupView.MASK) return
            val brush = state.brush
            state = state.copy(stroke = BrushStroke(state.frame, brush.mode, brush.radius, listOf(point(x, y))))
            showFixes()
        }

        override fun extend(x: Int, y: Int) {
            val stroke = state.stroke ?: return
            state = state.copy(stroke = stroke.copy(points = stroke.points + point(x, y)))
            showFixes()
        }

        override fun release() {
            val stroke = state.stroke ?: return
            edit { it.copy(fixes = it.fixes + stroke) }
            state = state.copy(stroke = null)
        }
    }

    /** The key-frame markers: what the filmstrip drags, and the buttons beneath it. */
    inner class Markers : StripGestures {

        /** Selects the marker within [reach] of [at] and shows its frame, or else moves the playhead there. */
        override fun press(at: Long, reach: Long) {
            val boundaries = state.keyFrames?.boundaries.orEmpty()
            val marker = boundaries.indices.minByOrNull { abs(boundaries[it] - at) }
                ?.takeIf { abs(boundaries[it] - at) <= reach }
            state = state.copy(marker = marker)
            selectFrame(marker?.let { boundaries[it] } ?: at.toInt())
        }

        /** Moves the selected marker to [at], as near as the rules allow, or else the playhead. */
        override fun drag(at: Long) {
            val keyFrames = state.keyFrames
            val marker = state.marker
            if (keyFrames == null || marker == null) {
                selectFrame(at.toInt())
                return
            }
            val moved = keyFrames.moved(marker, at.toInt(), state.frameCount)
            edit { it.copy(markers = moved.boundaries) }
            selectFrame(moved.boundaries[marker])
        }

        /** Adds a marker on the frame being looked at. */
        fun add() {
            val keyFrames = state.keyFrames ?: return
            val frame = state.frame
            val added = keyFrames.added(frame, state.frameCount)
            edit { it.copy(markers = added.boundaries) }
            state = state.copy(marker = added.boundaries.indexOf(frame))
        }

        /** Removes the selected marker. */
        fun remove() {
            val keyFrames = state.keyFrames ?: return
            val marker = state.marker ?: return
            if (!keyFrames.canRemove) return
            edit { it.copy(markers = keyFrames.removed(marker).boundaries) }
            state = state.copy(marker = null)
        }

        /** Drops the artist's markers for the stages' proposal. */
        fun useProposal() {
            edit { it.copy(markers = null) }
            state = state.copy(marker = null)
        }
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
        offUiThread({ state = state.copy(message = it) }, work)

    private class Preview(val picture: Raster, val plate: Raster, val mask: Mask)

    private companion object {
        /** A first guess at the empty wall: the spec's filming guide records it first. */
        const val FIRST_RANGE_MS = 1000L
    }
}
