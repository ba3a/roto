package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.profile.Clip
import com.roto.core.publish.FramePick
import com.roto.core.publish.Retime
import com.roto.core.review.Flipbook

/** The speeds a retime may take, as a percentage of the painted durations. */
val RETIME_SPEEDS: IntRange = 25..400

/**
 * The retime section's state: the [Retime] of one clip being edited, and what it makes of the clip's
 * handoff [base]: how many frames and how long. Nothing changes on disk until [save]. [player] plays the
 * retimed result; call [preview] after each change to load it. Every rule is `roto-core`'s.
 */
class RetimeModel(private val target: ClipTarget, clip: Clip, private val base: Flipbook) {

    /** The clip as the profile stores it. */
    var clip: Clip by mutableStateOf(clip)
        private set

    /** The retime being edited. */
    var retime: Retime by mutableStateOf(clip.retime)
        private set

    /** The player of the retimed result. */
    val player = FlipbookModel()

    /** How many frames the handoff has, before any retime. */
    val totalFrames: Int get() = base.frames.size

    /** How many frames the retime publishes. */
    val frameCount: Int get() = retime.keep?.size ?: totalFrames

    /** How long the published clip plays once, in milliseconds. */
    val totalMs: Long
        get() = retime.keep.orEmpty().ifEmpty { base.frames.indices.toList() }
            .sumOf { retime.duration(base.frames[it].durationMs).toLong() }

    /** Whether the edited retime differs from the one stored in the profile. */
    val changed: Boolean get() = retime != clip.retime

    /** Sets the speed to [percent], kept inside [RETIME_SPEEDS]. */
    fun setSpeed(percent: Int) {
        retime = retime.copy(speedPercent = percent.coerceIn(RETIME_SPEEDS))
    }

    /** Publishes [count] frames spread evenly over the handoff's, at least one and at most all of them. */
    fun useFrames(count: Int) {
        val n = count.coerceIn(1, totalFrames)
        retime = retime.copy(keep = if (n == totalFrames) null else FramePick.spread(totalFrames, n))
    }

    /** Goes back to the painted speed and every frame. */
    fun reset() {
        retime = Retime()
    }

    /** Loads the retimed result into [player]. */
    suspend fun preview() {
        val edited = retime
        player.open { base.retimed(edited) }
    }

    /** Stores the edited retime in the profile and returns the clip as stored; a failure goes to [onProblem], and the result is null. */
    suspend fun save(onProblem: (String?) -> Unit): Clip? {
        val saved = clip.copy(retime = retime)
        offUiThread(onProblem) {
            val repository = target.repository
            repository.save(target.profileId, repository.load(target.profileId).withClip(saved))
        } ?: return null
        clip = saved
        return saved
    }
}
