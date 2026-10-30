package com.roto.core.publish

import com.roto.core.ase.AseCodec
import com.roto.core.image.PngCodec
import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.Project
import com.roto.core.template.RelativePath
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Publishes a profile's finished clips into the consuming [project]:
 *
 * 1. each clip's [art][ArtFrames] is read from its handoff and written frame by frame where the
 *    project's `output` template says, with a [ClipManifest] where its `manifest` template says;
 *    frame files a longer earlier publish left beyond the new last frame are removed;
 * 2. a clip that opted in to mirroring is also published, [flipped][Mirror], as the facing its
 *    mirror pair names — never over a facing the profile has filmed itself;
 * 3. the project's `afterPublish` command runs last, in the project root.
 *
 * Every rule about names, sizes and places comes from `roto.json`; Roto knows nothing of what the
 * consuming project does with the files.
 */
class Publisher(private val project: Project, private val runner: ProcessRunner = SystemProcessRunner) {

    /** What a publish did: every [files] it wrote, and the `afterPublish` [hook]'s result, if one ran. */
    class Report(val files: List<Path>, val hook: ProcessResult?)

    /** Publishes [clips] — all of [profile]'s unless told otherwise — of the profile [profileId] stored in [folder]. */
    fun publish(profileId: String, profile: Profile, folder: ProfileFolder, clips: List<Clip> = profile.clips): Report {
        val written = try {
            require(clips.isNotEmpty()) { "There is nothing to publish" }
            checkMirrors(profile, clips)
            clips.flatMap { clip -> publishClip(profileId, clip, folder) }
        } catch (e: IllegalArgumentException) {
            throw PublishException(e.message ?: "Publish failed", e)
        }
        return Report(written, runHook(profileId))
    }

    /** The facing [clip] may be mirrored into: the one `roto.json` pairs it with, unless [profile] films that itself. */
    fun mirrorTarget(profile: Profile, clip: Clip): String? =
        project.config.mirrorFacing(clip.facing)?.takeIf { profile.clip(clip.action, it) == null }

    private fun publishClip(profileId: String, clip: Clip, folder: ProfileFolder): List<Path> {
        val handoff = folder.handoffFile(clip)
        require(Files.exists(handoff)) { "${clip.id} has no handoff yet; render it first" }
        val frames = ArtFrames.read(AseCodec.read(handoff), clip.id)
        val published = write(profileId, clip, clip.facing, frames, mirrored = false)
        val mirror = if (clip.mirror) {
            val facing = checkNotNull(project.config.mirrorFacing(clip.facing))
            val flipped = frames.map { ArtFrame(Mirror.flip(it.image), it.durationMs) }
            write(profileId, clip, facing, flipped, mirrored = true)
        } else {
            emptyList()
        }
        return published + mirror
    }

    private fun write(
        profileId: String,
        clip: Clip,
        facing: String,
        frames: List<ArtFrame>,
        mirrored: Boolean,
    ): List<Path> {
        val files = frames.mapIndexed { index, frame ->
            project.frameFile(profileId, clip.action, facing, index).also { PngCodec.write(frame.image, it) }
        }
        removeStaleFrames(profileId, clip.action, facing, from = frames.size)
        val manifestFile = project.manifestFile(profileId, clip.action, facing)
        val manifest = ClipManifest(
            action = clip.action,
            facing = facing,
            source = clip.id,
            mirrored = mirrored,
            loop = clip.loop,
            frames = files.zip(frames) { file, frame -> ManifestFrame(rootRelative(file), frame.durationMs) },
        )
        ClipManifestCodec.write(manifest, manifestFile)
        return files + listOf(manifestFile)
    }

    /** Deletes the frame files a longer earlier publish of this clip left past its new last frame. */
    private fun removeStaleFrames(profileId: String, action: String, facing: String, from: Int) {
        generateSequence(from) { it + 1 }
            .map { project.frameFile(profileId, action, facing, it) }
            .takeWhile { Files.isRegularFile(it) }
            .forEach { Files.delete(it) }
    }

    private fun checkMirrors(profile: Profile, clips: List<Clip>) {
        for (clip in clips.filter { it.mirror }) {
            val facing = requireNotNull(project.config.mirrorFacing(clip.facing)) {
                "${clip.id} asks to be mirrored, but roto.json pairs ${clip.facing} with no facing"
            }
            require(mirrorTarget(profile, clip) != null) {
                "${clip.id} asks to be mirrored into ${clip.action}_$facing, which is filmed itself; " +
                    "turn mirroring off for one of them"
            }
        }
    }

    private fun runHook(profileId: String): ProcessResult? {
        val command = project.afterPublishCommand(profileId)
        if (command.isEmpty()) return null
        val resolved = listOf(program(command.first())) + command.drop(1)
        val result = try {
            runner.run(resolved, project.root)
        } catch (e: IOException) {
            throw PublishException("The frames are published, but afterPublish could not start: ${e.message}", e)
        }
        if (result.exitCode != 0) {
            val reason = result.stderr.lines().filter { it.isNotBlank() }.takeLast(ERROR_LINES).joinToString("\n")
            throw PublishException(
                "The frames are published, but afterPublish failed with exit code ${result.exitCode}: $reason",
            )
        }
        return result
    }

    /**
     * The program to start: a path like `./gradlew.bat` or `tools/sync` is resolved inside the project
     * root, since Windows would look for it beside Roto instead; a bare name is left for `PATH`.
     */
    private fun program(name: String): String =
        if ('/' in name) {
            RelativePath.resolve(project.root, name.removePrefix("./"), "afterPublish program").toString()
        } else {
            name
        }

    private fun rootRelative(file: Path): String = project.root.relativize(file).joinToString("/")

    private companion object {
        const val ERROR_LINES = 5
    }
}
