package com.roto.cli

import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.ProfileRepository
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.publish.Publisher
import com.roto.core.render.ProfileRenderer
import com.roto.core.video.Ffmpeg
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * The `roto` command: reads the command line, opens the project and the profile, and hands them to
 * [ProfileRenderer] or [Publisher]. It only reports what they did — every rule lives in `roto-core`.
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
) {

    fun run(args: List<String>): Int {
        if (Arguments.wantsHelp(args)) {
            out.println(Arguments.usage)
            return SUCCESS
        }
        val invocation = try {
            Arguments.parse(args, environment, workingDirectory)
        } catch (e: UsageException) {
            err.println("roto: ${e.message}")
            err.println()
            err.println(Arguments.usage)
            return USAGE
        }
        return try {
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
        if (!Files.isRegularFile(invocation.project.resolve(ProjectConfigCodec.fileName))) {
            throw IOException("${invocation.project} holds no ${ProjectConfigCodec.fileName}; pass --project <folder>")
        }
        val project = Project.open(invocation.project)
        val repository = ProfileRepository(invocation.library, project.config.id)
        if (!repository.exists(invocation.profile)) {
            throw IOException("No profile '${invocation.profile}' in ${repository.projectFolder}")
        }
        val profile = repository.load(invocation.profile)
        val folder = repository.folder(invocation.profile)
        when (invocation.command) {
            Command.RENDER -> {
                val report = ProfileRenderer(project, Ffmpeg(runner)).render(profile, folder)
                report.files.forEach { out.println("Rendered $it") }
            }
            Command.PUBLISH -> {
                val report = Publisher(project, runner).publish(invocation.profile, profile, folder)
                report.files.forEach { out.println("Published $it") }
                report.hook?.let { hook ->
                    out.print(hook.stdout.toString(Charsets.UTF_8))
                    if (hook.stderr.isNotBlank()) err.print(hook.stderr)
                }
            }
        }
    }

    companion object {
        const val SUCCESS = 0
        const val FAILURE = 1
        const val USAGE = 2
    }
}
