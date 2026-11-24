package com.roto.cli

import com.roto.core.model.AseModelSource
import com.roto.core.model.AtlasModelSource
import com.roto.core.model.ConfirmedRow
import com.roto.core.model.ModelImporter
import com.roto.core.model.ModelRow
import com.roto.core.model.ModelSource
import com.roto.core.model.PngFolderModelSource
import com.roto.core.model.defaultAtlasPattern
import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.preset.BrokenPreset
import com.roto.core.preset.LibraryPreset
import com.roto.core.preset.PresetBundle
import com.roto.core.preset.PresetLibrary
import com.roto.core.preset.ProjectExporter
import com.roto.core.preset.ProjectScaffolder
import com.roto.core.profile.ProfileRepository
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfig
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.publish.Publisher
import com.roto.core.render.ProfileRenderer
import com.roto.core.settings.SettingsStore
import com.roto.core.template.Template
import com.roto.core.video.Ffmpeg
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * The `roto` command: reads the command line, opens the project and the profile, and hands them to
 * [ProfileRenderer] or [Publisher]; `init` and `export` go to [ProjectScaffolder] and [ProjectExporter].
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
                Arguments.parse(args, environment, workingDirectory) { settings.load().libraryFolder }
            } catch (e: UsageException) {
                err.println("roto: ${e.message}")
                err.println()
                err.println(Arguments.usage)
                return USAGE
            }
            execute(invocation)
            SUCCESS
        } catch (e: NoSuchFileException) {
            err.println("roto: no such file: ${e.file}")
            FAILURE
        } catch (e: IOException) {
            err.println("roto: ${e.message}")
            FAILURE
        }
    }

    private fun execute(invocation: Invocation) {
        when (invocation) {
            is ProfileInvocation -> runProfile(invocation)
            is InitInvocation -> init(invocation)
            is ExportInvocation -> export(invocation)
            is ImportModelInvocation -> importModel(invocation)
        }
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
        val source = modelSource(invocation, project.config)
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

    private fun modelSource(invocation: ImportModelInvocation, config: ProjectConfig): ModelSource {
        val path = invocation.source
        val name = path.fileName.toString().lowercase()
        return when {
            Files.isDirectory(path) ->
                PngFolderModelSource(path, config.canvas, invocation.durationMs, MODEL_LOOPS)
            !Files.isRegularFile(path) -> throw NoSuchFileException(path.toString())
            name.endsWith(".ase") || name.endsWith(".aseprite") -> AseModelSource(path, config.canvas, MODEL_LOOPS)
            name.endsWith(".atlas") -> atlas(invocation, config)
            else -> throw IOException("$path is not a PNG folder, .ase, .aseprite or .atlas file")
        }
    }

    private fun atlas(invocation: ImportModelInvocation, config: ProjectConfig): ModelSource =
        try {
            AtlasModelSource(
                invocation.source,
                config.canvas,
                config.actions,
                config.facings,
                Template(invocation.pattern ?: defaultAtlasPattern(config.output)),
                config.actions.associateWith { invocation.durationMs },
                config.actions.toSet(),
            )
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message ?: "Bad --pattern", e)
        }

    private fun runProfile(invocation: ProfileInvocation) {
        val project = openProject(invocation.project)
        val repository = ProfileRepository(invocation.library, project.config.id)
        if (!repository.exists(invocation.profile)) {
            throw IOException("No profile '${invocation.profile}' in ${repository.projectFolder}")
        }
        val profile = repository.load(invocation.profile)
        val folder = repository.folder(invocation.profile)
        when (invocation.command) {
            Command.PUBLISH -> {
                val report = Publisher(project, runner).publish(invocation.profile, profile, folder)
                report.files.forEach { out.println("Published $it") }
                report.hook?.let { hook ->
                    out.print(hook.stdout.toString(Charsets.UTF_8))
                    if (hook.stderr.isNotBlank()) err.print(hook.stderr)
                }
            }
            else -> {
                val report = ProfileRenderer(project, Ffmpeg(runner)).render(profile, folder)
                report.files.forEach { out.println("Rendered $it") }
            }
        }
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

/** Imported models loop; the artist changes that by editing the model, not on the command line. */
private const val MODEL_LOOPS = true
