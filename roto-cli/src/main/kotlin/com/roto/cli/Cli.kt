package com.roto.cli

import com.roto.core.ase.AseCodec
import com.roto.core.coverage.CoverageReader
import com.roto.core.model.ConfirmedRow
import com.roto.core.model.ModelImporter
import com.roto.core.model.ModelRepository
import com.roto.core.model.ModelRow
import com.roto.core.model.ModelSources
import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.preset.BrokenPreset
import com.roto.core.preset.LibraryPreset
import com.roto.core.preset.PresetBundle
import com.roto.core.preset.PresetLibrary
import com.roto.core.preset.ProjectExporter
import com.roto.core.preset.ProjectScaffolder
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.ProfileRepository
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.publish.ArtFrames
import com.roto.core.publish.BaseProfile
import com.roto.core.publish.Publisher
import com.roto.core.render.ProfileRenderer
import com.roto.core.review.ArtLint
import com.roto.core.review.LintContexts
import com.roto.core.settings.SettingsStore
import com.roto.core.video.Ffmpeg
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * The `roto` command: reads the command line, opens the project and the profile, and hands them to
 * [ProfileRenderer], [Publisher], [CoverageReader] or [ArtLint]; `init` and `export` go to [ProjectScaffolder] and [ProjectExporter].
 * It only reports what they did — every rule lives in `roto-core`.
 *
 * Exit codes: [SUCCESS], [FAILURE] when the work stopped (its reason on [err]), [USAGE] when the
 * command line made no sense.
 */
class Cli(
    private val out: PrintStream,
    private val err: PrintStream,
    private val environment: Map<String, String> = System.getenv(),
    private val workingDirectory: Path = Path.of("").toAbsolutePath(),
    private val runner: ProcessRunner = SystemProcessRunner,
    private val settings: SettingsStore = SettingsStore.default(),
) {

    /** Runs the command in [args]; returns [SUCCESS], [FAILURE] or [USAGE]. */
    fun run(args: List<String>): Int {
        if (Arguments.wantsHelp(args)) {
            out.println(Arguments.usage)
            return SUCCESS
        }
        return try {
            val invocation = try {
                Arguments.parse(args, environment, workingDirectory) { settings.library() }
            } catch (e: UsageException) {
                err.println("roto: ${e.message}")
                err.println()
                err.println(Arguments.usage)
                return USAGE
            }
            execute(invocation)
        } catch (e: NoSuchFileException) {
            err.println("roto: no such file: ${e.file}")
            FAILURE
        } catch (e: IOException) {
            err.println("roto: ${e.message}")
            FAILURE
        }
    }

    private fun execute(invocation: Invocation): Int {
        when (invocation) {
            is ProfileInvocation -> return runProfile(invocation)
            is InitInvocation -> init(invocation)
            is ExportInvocation -> export(invocation)
            is ImportModelInvocation -> importModel(invocation)
        }
        return SUCCESS
    }

    private fun openProject(folder: Path): Project {
        if (!Files.isRegularFile(folder.resolve(ProjectConfigCodec.fileName))) {
            throw IOException("$folder holds no ${ProjectConfigCodec.fileName}; pass --project <folder>")
        }
        return Project.open(folder)
    }

    private fun init(invocation: InitInvocation) {
        val entry = PresetLibrary.standard(settings.configFolder).list().firstOrNull { it.id == invocation.preset }
        val preset = when (entry) {
            is LibraryPreset -> entry.preset
            is BrokenPreset -> throw IOException("Preset '${entry.id}' cannot be read: ${entry.message}")
            null -> throw IOException("No preset '${invocation.preset}'")
        }
        val project = ProjectScaffolder.scaffold(preset, invocation.id, invocation.folder)
        out.println("Created project ${project.config.id} in ${project.root}")
    }

    private fun export(invocation: ExportInvocation) {
        val project = openProject(invocation.project)
        val name = invocation.file.fileName.toString().removeSuffix(".${PresetBundle.EXTENSION}")
        PresetBundle.write(ProjectExporter.export(project, name, ""), invocation.file)
        out.println("Exported $name to ${invocation.file}")
    }

    private fun importModel(invocation: ImportModelInvocation) {
        val project = openProject(invocation.project)
        val repository = ProfileRepository(invocation.library, project.config.id)
        if (!repository.exists(invocation.profile)) {
            throw IOException("No profile '${invocation.profile}' in ${repository.projectFolder}")
        }
        val source = ModelSources.open(invocation.source, project.config, invocation.durationMs, invocation.pattern)
        val rows = source.rows().map {
            val action = invocation.action ?: needed(it.action, "action", it)
            ConfirmedRow(it, action, invocation.facing ?: needed(it.facing, "facing", it))
        }
        val keys = ModelImporter(repository.folder(invocation.profile), project.config)
            .import(source, rows, invocation.source.fileName.toString(), invocation.replace)
        keys.forEach { out.println("Imported model $it") }
    }

    private fun needed(guess: String?, what: String, row: ModelRow): String =
        guess ?: throw IOException("Cannot tell the $what of '${row.id}'; pass --$what")

    private fun runProfile(invocation: ProfileInvocation): Int {
        val project = openProject(invocation.project)
        val repository = ProfileRepository(invocation.library, project.config.id)
        if (!repository.exists(invocation.profile)) {
            throw IOException("No profile '${invocation.profile}' in ${repository.projectFolder}")
        }
        val profile = repository.load(invocation.profile)
        val folder = repository.folder(invocation.profile)
        // Only publish and status read a variant's base, so a missing base cannot stop the other commands.
        val base by lazy {
            profile.variant?.let { BaseProfile(repository.load(it.base), repository.folder(it.base)) }
        }
        when (invocation.command) {
            Command.PUBLISH -> {
                val report = Publisher(project, runner).publish(invocation.profile, profile, folder, base = base)
                report.files.forEach { out.println("Published $it") }
                if (report.skipped.isNotEmpty()) {
                    val names = report.skipped.joinToString()
                    out.println("Skipped ${report.skipped.size} unfinished base clips: $names")
                }
                report.hook?.let { hook ->
                    out.print(hook.stdout.toString(Charsets.UTF_8))
                    if (hook.stderr.isNotBlank()) err.print(hook.stderr)
                }
            }
            Command.STATUS -> {
                val matrix = CoverageReader(project, invocation.profile, profile, folder, base).read()
                out.println(StatusFormatter.format(matrix))
            }
            Command.CHECK -> return check(project, profile, folder)
            else -> {
                val report = ProfileRenderer(project, Ffmpeg(runner)).render(profile, folder)
                report.files.forEach { out.println("Rendered $it") }
            }
        }
        return SUCCESS
    }

    /** Lints every clip that has a handoff, without writing anything; [FAILURE] when anything is found. */
    private fun check(project: Project, profile: Profile, folder: ProfileFolder): Int {
        val models = ModelRepository(folder)
        val checks = profile.clips.filter { Files.exists(folder.handoffFile(it)) }.map { clip ->
            try {
                val sprite = AseCodec.read(folder.handoffFile(clip))
                val frames = ArtFrames.readPartial(sprite, clip.id)
                val context = LintContexts.of(project, clip, models, sprite.frames.map { it.durationMs })
                ClipCheck.Checked(clip.id, ArtLint.run(frames, context))
            } catch (e: IOException) {
                ClipCheck.Unreadable(clip.id, e.message ?: "The handoff of ${clip.id} cannot be read")
            } catch (e: IllegalArgumentException) {
                ClipCheck.Unreadable(clip.id, e.message ?: "The handoff of ${clip.id} cannot be read")
            }
        }
        out.println(LintFormatter.format(checks))
        return if (LintFormatter.hasFindings(checks)) FAILURE else SUCCESS
    }

    /** Exit codes. */
    companion object {

        /** The work finished. */
        const val SUCCESS = 0
        /** The work stopped; the reason is on the error stream. */
        const val FAILURE = 1
        /** The command line could not be understood. */
        const val USAGE = 2
    }
}
