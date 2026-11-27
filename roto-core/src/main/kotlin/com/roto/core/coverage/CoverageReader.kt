package com.roto.core.coverage

import com.roto.core.ase.AseCodec
import com.roto.core.image.PngCodec
import com.roto.core.model.ModelRepository
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.Project
import com.roto.core.publish.ArtFrame
import com.roto.core.publish.ArtProgress
import com.roto.core.publish.ClipManifestCodec
import com.roto.core.publish.PublishedFrames
import com.roto.core.publish.Publisher
import com.roto.core.template.RelativePath
import java.io.IOException
import java.nio.file.Files

/**
 * Reads where each action and facing of one profile stands: from its clips, their handoffs, the mirror
 * opt-in, its models and what was published for it.
 *
 * It reads one cell at a time, so a caller can fill a matrix cell by cell off the UI thread. Reading
 * changes nothing on disk, and the same files give equal results.
 *
 * @property project the consuming project the profile publishes into
 * @property profileId the profile's id, which the output templates take as `{profile}`
 * @property profile the profile to read
 * @property folder the profile's folder
 */
class CoverageReader(
    private val project: Project,
    private val profileId: String,
    private val profile: Profile,
    private val folder: ProfileFolder,
) {

    private val models = ModelRepository(folder)
    private val publisher = Publisher(project)

    /** The cell of [action] and [facing], which both must be in `roto.json`. */
    fun cell(action: String, facing: String): CoverageCell {
        val hasModel = models.exists("${action}_$facing")
        val clip = profile.clip(action, facing)
        val state = when {
            clip != null -> clipState(clip)
            isMirrored(action, facing) -> CoverageState.Mirrored
            hasModel -> CoverageState.Model
            else -> CoverageState.NotFilmed
        }
        return CoverageCell(state, hasModel)
    }

    /** Every cell of the project's actions and facings, as a matrix. */
    fun read(): CoverageMatrix {
        val config = project.config
        val cells = config.actions.flatMap { action -> config.facings.map { facing -> action to facing } }
            .associateWith { (action, facing) -> cell(action, facing) }
        return CoverageMatrix.of(config, cells)
    }

    private fun isMirrored(action: String, facing: String): Boolean = profile.clips.any {
        it.mirror && it.action == action && publisher.mirrorTarget(profile, it) == facing
    }

    private fun clipState(clip: Clip): CoverageState =
        when (val progress = ArtProgress.of(folder.handoffFile(clip), clip.id)) {
            ArtProgress.NoHandoff -> CoverageState.Setup
            is ArtProgress.Unreadable -> CoverageState.Unreadable(progress.reason)
            is ArtProgress.Painted ->
                if (progress.finished) {
                    finishedState(clip)
                } else {
                    CoverageState.Painting(progress.painted, progress.total)
                }
        }

    private fun finishedState(clip: Clip): CoverageState {
        val frames = try {
            PublishedFrames.of(clip, AseCodec.read(folder.handoffFile(clip)))
        } catch (e: IOException) {
            return CoverageState.Unreadable(e.message ?: "The handoff of ${clip.id} cannot be read")
        } catch (e: IllegalArgumentException) {
            return CoverageState.Unreadable(e.message ?: "The handoff of ${clip.id} cannot be read")
        }
        if (!Files.exists(project.manifestFile(profileId, clip.action, clip.facing))) return CoverageState.Finished
        val mirror = publisher.mirrorTarget(profile, clip)?.takeIf { clip.mirror }
        val targets = listOfNotNull(
            Target(clip.facing, frames),
            mirror?.let { Target(it, PublishedFrames.mirrored(frames)) },
        )
        val difference = targets.firstNotNullOfOrNull { difference(clip, it) }
        return if (difference == null) CoverageState.Published else CoverageState.OutOfDate(difference)
    }

    /** The frames one facing of a clip should have published. */
    private class Target(val facing: String, val frames: List<ArtFrame>)

    /** Why what is published for [target] differs from its frames, or null when it matches. */
    private fun difference(clip: Clip, target: Target): String? {
        val manifestFile = project.manifestFile(profileId, clip.action, target.facing)
        if (!Files.exists(manifestFile)) return "${clip.action}_${target.facing} is not published"
        val manifest = try {
            ClipManifestCodec.read(manifestFile)
        } catch (e: IOException) {
            return e.message ?: "The manifest $manifestFile cannot be read"
        }
        if (manifest.frames.size != target.frames.size) {
            return "${clip.action}_${target.facing} publishes ${manifest.frames.size} frames, " +
                "the art has ${target.frames.size}"
        }
        return manifest.frames.zip(target.frames).withIndex().firstNotNullOfOrNull { (index, pair) ->
            val (published, art) = pair
            val reason = if (published.durationMs != art.durationMs) {
                "shows for a different time"
            } else {
                frameDifference(published.file, art)
            }
            reason?.let { "Frame ${index + 1} $it" }
        }
    }

    private fun frameDifference(path: String, art: ArtFrame): String? {
        val image = try {
            PngCodec.read(RelativePath.resolve(project.root, path, "frame file"))
        } catch (e: IOException) {
            return "cannot be read: ${e.message}"
        } catch (e: IllegalArgumentException) {
            return "cannot be read: ${e.message}"
        }
        return if (image == art.image) null else "differs from the art"
    }
}
