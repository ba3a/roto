package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.ase.AseCodec
import com.roto.core.model.ModelClip
import com.roto.core.model.ModelException
import com.roto.core.model.ModelRepository
import com.roto.core.palette.ProjectPalette
import com.roto.core.process.ProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.project.Project
import com.roto.core.publish.ArtFrames
import com.roto.core.publish.ArtProgress
import com.roto.core.publish.Publisher
import com.roto.core.render.ProfileRenderer
import com.roto.core.review.ArtLint
import com.roto.core.review.Flipbook
import com.roto.core.review.LintContexts
import com.roto.core.review.LintReport

/**
 * One clip on the panel: how far its [art] has come, and the facing it [mirrorsInto], if it may be mirrored.
 *
 * @property clip the clip
 * @property art how far its handoff has been painted
 * @property mirrorsInto the facing it may be mirrored into, or null
 */
data class HandoffClip(val clip: Clip, val art: ArtProgress, val mirrorsInto: String?)

/**
 * What the handoff panel shows: the profile's [clips], what is [running], if anything, a [report] on
 * the last thing done, and a [message] about the last thing that went wrong.
 *
 * @property clips the profile's clips
 * @property running what is being done, if anything
 * @property report a report on the last thing done
 * @property message the last thing that went wrong
 */
data class HandoffState(
    val clips: List<HandoffClip> = emptyList(),
    val running: String? = null,
    val report: String? = null,
    val message: String? = null,
) {
    /** The clips a publish takes: those with art on every frame. */
    val finished: List<Clip> get() = clips.filter { it.art.finished }.map { it.clip }
}

/**
 * The handoff panel's state and what can be done to it: render clips into their handoffs, open one
 * in the artist's [editor], opt a clip in to mirroring, and publish whatever is finished. A publish
 * can be repeated freely, so it takes the clips that are done and leaves the rest for later. The
 * handoffs are the artist's to change at any time, so [refresh] reads them again. Every rule is
 * `roto-core`'s, and work runs off the UI thread.
 */
class HandoffModel(
    private val target: ClipTarget,
    private val project: Project,
    private val editor: Editor,
    runner: ProcessRunner,
) {
    private val folder = target.repository.folder(target.profileId)
    private val publisher = Publisher(project, runner)

    /** What the panel shows. */
    var state by mutableStateOf(HandoffState())
        private set

    /** The id of the profile being handed off. */
    val profileId: String get() = target.profileId

    /** The clip being played, or null. */
    var watched: Clip? by mutableStateOf(null)
        private set

    /** The player of [watched], or null. */
    var flipbook: FlipbookModel? by mutableStateOf(null)
        private set

    /** The lint of [watched], or null. */
    var lint: LintModel? by mutableStateOf(null)
        private set

    /** Whether the player shows the clip flipped, as its mirrored publish would. */
    var watchedMirrored: Boolean by mutableStateOf(false)
        private set

    /** The colours the project's ramps hold, in ramp order: what the player may draw behind a frame. */
    var rampColours: List<Int> by mutableStateOf(emptyList())
        private set

    /**
     * Plays [clip]'s handoff, flipped when [mirrored], with the clip's model as its ghost if it has one.
     * The handoff is read again here, so a save in the editor shows on the next call.
     */
    suspend fun watch(clip: Clip, mirrored: Boolean = false) {
        val kept = flipbook?.takeIf { watched?.id == clip.id }
        val player = kept ?: working {
            rampColours = ProjectPalette.load(project).ramps.ramps.flatMap { it.colors }
            FlipbookModel(ghostOf(clip))
        } ?: return
        watched = clip
        watchedMirrored = mirrored
        flipbook = player
        val linting = LintModel(player, mirrored)
        lint = linting
        player.open { Flipbook.of(AseCodec.read(folder.handoffFile(clip)), clip.id, clip.loop, mirrored) }
        linting.run { lintOf(clip) }
    }

    /** Lints the art of [clip]'s handoff as it stands on disk, never writing to it. */
    private fun lintOf(clip: Clip): LintReport {
        val sprite = AseCodec.read(folder.handoffFile(clip))
        val context = LintContexts.of(project, clip, ModelRepository(folder), sprite.frames.map { it.durationMs })
        return ArtLint.run(ArtFrames.readPartial(sprite, clip.id), context)
    }

    /** The clip's chosen model, or null when it has none or the model cannot be read: the ghost stays off. */
    private fun ghostOf(clip: Clip): ModelClip? {
        val key = clip.model ?: return null
        return try {
            ModelRepository(folder).load(key)
        } catch (_: ModelException) {
            null
        }
    }

    /** Stops playing; the player goes. */
    fun stopWatching() {
        watched = null
        flipbook = null
        lint = null
    }

    /** Reads the profile and every handoff again. */
    suspend fun refresh() {
        val clips = working {
            val profile = load()
            profile.clips.map { clip ->
                val art = ArtProgress.of(folder.handoffFile(clip), clip.id)
                HandoffClip(clip, art, publisher.mirrorTarget(profile, clip))
            }
        }
        if (clips != null) state = state.copy(clips = clips)
    }

    /** Renders [clips] into their handoffs, every clip's unless told otherwise; what is painted stays. */
    suspend fun render(clips: List<Clip> = state.clips.map { it.clip }) = run("Rendering…") {
        val report = ProfileRenderer(project, target.ffmpeg).render(load(), folder, clips)
        "Rendered ${clipCount(report.files.size)}."
    }

    /** Publishes the finished clips and runs the project's `afterPublish`. */
    suspend fun publish() {
        val clips = state.finished
        run("Publishing…") {
            val report = publisher.publish(target.profileId, load(), folder, clips)
            val hook = if (report.hook == null) "" else ", and afterPublish ran"
            "Published ${clipCount(clips.size)}$hook."
        }
    }

    /** Opens [clip]'s handoff for painting, and reads it again once the editor is done with it. */
    suspend fun paint(clip: Clip) {
        state = state.copy(message = null)
        working { editor.open(folder.handoffFile(clip)) }
        refresh()
    }

    /** Opts [clip] in to or out of being published mirrored as well. */
    suspend fun setMirror(clip: Clip, mirror: Boolean) {
        working { target.repository.save(target.profileId, load().withClip(clip.copy(mirror = mirror))) }
        refresh()
    }

    private suspend fun run(activity: String, work: () -> String) {
        if (state.running != null) return
        state = state.copy(running = activity, report = null, message = null)
        val report = working(work)
        state = state.copy(running = null, report = report)
        refresh()
    }

    private fun load(): Profile = target.repository.load(target.profileId)

    private suspend fun <T : Any> working(work: () -> T): T? =
        offUiThread({ state = state.copy(message = it) }, work)
}
