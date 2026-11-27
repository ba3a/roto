package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.image.Raster
import com.roto.core.model.ModelClip
import com.roto.core.review.Flipbook
import com.roto.core.review.FlipbookClock
import com.roto.core.review.FlipbookFrame
import com.roto.core.review.LoopSeam
import com.roto.core.review.OnionSkin
import com.roto.core.review.SeamDiff

/** What the player draws behind the frame. */
sealed interface FlipbookBackground {
    /** A light and dark checker. */
    data object Checker : FlipbookBackground

    /** A flat mid grey. */
    data object MidGrey : FlipbookBackground

    /**
     * One colour, a ramp step the artist picked.
     *
     * @property argb the colour as `0xAARRGGBB`
     */
    data class Colour(val argb: Int) : FlipbookBackground
}

/** The playback speeds, as a percentage of the frames' own durations. */
val FLIPBOOK_SPEEDS: List<Int> = listOf(25, 50, 100)

/** The zoom range of the player: whole-number multiples of the canvas. */
val FLIPBOOK_ZOOMS: IntRange = 1..MAX_FLIPBOOK_ZOOM

private const val MAX_FLIPBOOK_ZOOM = 16
private const val PERCENT = 100

/** Whether the player has a flipbook to play. */
sealed interface FlipbookLoad {
    /** The handoff is being read. */
    data object Loading : FlipbookLoad

    /**
     * The handoff is read.
     *
     * @property flipbook the frames and whether they loop
     * @property seam the diff of the last frame against the first, or null for a single frame
     */
    class Ready(val flipbook: Flipbook, val seam: SeamDiff?) : FlipbookLoad

    /**
     * The handoff cannot be read, so the player is off.
     *
     * @property reason why, as the artist reads it
     */
    data class Disabled(val reason: String) : FlipbookLoad
}

/**
 * The player's state: a clip's [Flipbook], where playback is, and the view options. Time comes in from
 * the caller through [tick]; the model asks [FlipbookClock] which frame shows. The picture is a pure
 * function of this state.
 *
 * @property modelGhost the clip's model, shown behind the frame at its mapped frame when [ghostOn]
 */
class FlipbookModel(private val modelGhost: ModelClip? = null) {
    /** Whether the flipbook is loading, ready or off. */
    var load: FlipbookLoad by mutableStateOf(FlipbookLoad.Loading)
        private set

    /** The index of the frame shown. */
    var index: Int by mutableStateOf(0)
        private set

    /** Whether playback runs. */
    var playing: Boolean by mutableStateOf(false)
        private set

    /** The playback speed, one of [FLIPBOOK_SPEEDS]. */
    var speed: Int by mutableStateOf(PERCENT)
        private set

    /** The zoom, in [FLIPBOOK_ZOOMS]. */
    var zoom: Int by mutableStateOf(1)
        private set

    /** What is drawn behind the frame. */
    var background: FlipbookBackground by mutableStateOf(FlipbookBackground.Checker)
        private set

    /** Whether the previous and next frames show behind the current one. */
    var onionOn: Boolean by mutableStateOf(false)
        private set

    /** Whether the loop seam diff shows. */
    var seamOn: Boolean by mutableStateOf(false)
        private set

    /** Whether the model's frame shows behind the current one. */
    var ghostOn: Boolean by mutableStateOf(false)
        private set

    /** Whether the clip has a model, so the ghost can show. */
    val hasModel: Boolean get() = modelGhost != null

    private var timeMs = 0L
    private var lastElapsedMs = 0L
    private var originElapsedMs = 0L

    private val flipbook: Flipbook? get() = (load as? FlipbookLoad.Ready)?.flipbook

    /** The frame shown, or null while nothing is loaded. */
    val frame: FlipbookFrame? get() = flipbook?.frames?.getOrNull(index)

    /** The onion skin of the current frame, or null when it is off or nothing is loaded. */
    val onion: Raster?
        get() {
            val book = flipbook?.takeIf { onionOn } ?: return null
            val last = book.frames.lastIndex
            val previous = if (index > 0) index - 1 else if (book.loop) last else -1
            val next = if (index < last) index + 1 else if (book.loop) 0 else -1
            return OnionSkin.compose(
                book.frames[index].image,
                book.frames.getOrNull(previous)?.image,
                book.frames.getOrNull(next)?.image,
            )
        }

    /** The loop seam diff, or null when it is off, there is one frame or nothing is loaded. */
    val seam: SeamDiff? get() = (load as? FlipbookLoad.Ready)?.seam?.takeIf { seamOn }

    /** The model frame mapped to the current frame, or null when the ghost is off or the clip has no model. */
    val ghost: Raster?
        get() {
            val model = modelGhost?.takeIf { ghostOn } ?: return null
            val count = flipbook?.frames?.size ?: return null
            return model.frames[model.frameIndexFor(index, count)].raster
        }

    /**
     * Reads the flipbook through [read] off the UI thread. A handoff that cannot be read turns the player
     * off with the reason.
     */
    suspend fun open(read: () -> Flipbook) {
        load = FlipbookLoad.Loading
        playing = false
        index = 0
        var problem: String? = null
        val book = offUiThread({ problem = it }) { read() }
        load = if (book == null) {
            FlipbookLoad.Disabled(problem ?: "The handoff cannot be read")
        } else {
            val frames = book.frames
            val seam = if (frames.size > 1) LoopSeam.diff(frames.last().image, frames.first().image) else null
            FlipbookLoad.Ready(book, seam)
        }
    }

    /** Starts playing from the current frame; a clip that does not loop and stands on its last frame starts over. */
    fun play() {
        val book = flipbook ?: return
        if (!book.loop && index == book.frames.lastIndex) index = 0
        timeMs = book.durations.take(index).sumOf { it.toLong() }
        originElapsedMs = lastElapsedMs
        playing = true
    }

    /** Stops playing on the current frame. */
    fun pause() {
        playing = false
    }

    /**
     * Feeds the caller's clock, in milliseconds since any fixed start. Call it on every frame, paused or
     * not. While playing, the time that passed since the last tick, scaled by [speed], moves the frame.
     */
    fun tick(elapsedMs: Long) {
        val book = flipbook
        if (playing && book != null) {
            val time = timeMs + (elapsedMs - originElapsedMs) * speed / PERCENT
            index = FlipbookClock.frameAt(time, book.durations, book.loop)
            if (!book.loop && time >= book.durations.sumOf { it.toLong() }) playing = false
        }
        lastElapsedMs = elapsedMs
    }

    /** Pauses and moves [by] frames: wraps round for a looping clip, stops at the ends otherwise. */
    fun step(by: Int) {
        val book = flipbook ?: return
        playing = false
        val count = book.frames.size
        index = if (book.loop) Math.floorMod(index + by, count) else (index + by).coerceIn(0, count - 1)
    }

    /** Sets the speed to [percent], one of [FLIPBOOK_SPEEDS]; playback carries on from where it is. */
    fun chooseSpeed(percent: Int) {
        require(percent in FLIPBOOK_SPEEDS) { "Speed $percent % is not one of $FLIPBOOK_SPEEDS" }
        if (playing) rebase()
        speed = percent
    }

    /** Sets the zoom, kept inside [FLIPBOOK_ZOOMS]. */
    fun chooseZoom(value: Int) {
        zoom = value.coerceIn(FLIPBOOK_ZOOMS)
    }

    /** Sets what is drawn behind the frame. */
    fun chooseBackground(value: FlipbookBackground) {
        background = value
    }

    /** Turns the onion skin on or off. */
    fun toggleOnion() {
        onionOn = !onionOn
    }

    /** Turns the loop seam diff on or off. */
    fun toggleSeam() {
        seamOn = !seamOn
    }

    /** Turns the model ghost on or off; without a model it stays off. */
    fun toggleGhost() {
        ghostOn = !ghostOn && modelGhost != null
    }

    private fun rebase() {
        val book = flipbook ?: return
        timeMs += (lastElapsedMs - originElapsedMs) * speed / PERCENT
        timeMs = timeMs.coerceAtMost(book.durations.sumOf { it.toLong() })
        originElapsedMs = lastElapsedMs
    }
}
