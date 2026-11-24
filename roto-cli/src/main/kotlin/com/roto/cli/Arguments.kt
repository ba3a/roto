package com.roto.cli

import com.roto.core.preset.PresetBundle
import com.roto.core.template.SafeName
import java.nio.file.Path

/** What `roto` can be asked to do. */
internal enum class Command(val word: String, val summary: String) {
    RENDER("render", "Render the handoff of every clip in a profile"),
    PUBLISH("publish", "Publish the finished art of every clip in a profile"),
    INIT("init", "Create a project in a folder from a preset"),
    EXPORT("export", "Write the project as a ${PresetBundle.EXTENSION} bundle"),
}

/** One run of `roto`. */
internal sealed interface Invocation

/** [command] on the profile [profile] of the [project] folder, stored in [library]. */
internal data class ProfileInvocation(
    val command: Command,
    val profile: String,
    val project: Path,
    val library: Path,
) : Invocation

/** `roto init`: create the project [id] in [folder] from the preset named [preset]. */
internal data class InitInvocation(val folder: Path, val preset: String, val id: String) : Invocation

/** `roto export`: write the [project] folder's setup to the bundle [file]. */
internal data class ExportInvocation(val file: Path, val project: Path) : Invocation

/** A command line `roto` cannot make sense of; its message says what is wrong. */
internal class UsageException(message: String) : Exception(message)

/**
 * Reads `roto <command> ...`. `render` and `publish` take a profile and `[--project <folder>]
 * [--library <folder>]`; `init <folder> --preset <name> --id <id>` creates a project; `export
 * <file>` writes the project of `--project` as a bundle. The project defaults to the working folder;
 * the library to the [LIBRARY_VARIABLE] environment variable, then to the library chosen in the app.
 * Relative paths are taken from the working folder. An option's value may follow it or be joined with `=`.
 */
internal object Arguments {

    const val LIBRARY_VARIABLE = "ROTO_LIBRARY"
    private const val PROJECT = "--project"
    private const val LIBRARY = "--library"
    private const val PRESET = "--preset"
    private const val ID = "--id"
    private const val COLUMN = 12

    /** What each option's value is called in a message. */
    private val values = mapOf(PROJECT to "folder", LIBRARY to "folder", PRESET to "name", ID to "id")

    /** The options each command takes. */
    private val allowed = mapOf(
        Command.RENDER to setOf(PROJECT, LIBRARY),
        Command.PUBLISH to setOf(PROJECT, LIBRARY),
        Command.INIT to setOf(PRESET, ID),
        Command.EXPORT to setOf(PROJECT),
    )

    val usage: String = buildString {
        appendLine("Usage: roto <render|publish> <profile> [$PROJECT <folder>] [$LIBRARY <folder>]")
        appendLine("       roto init <folder> $PRESET <name> $ID <id>")
        appendLine("       roto export <name.${PresetBundle.EXTENSION}> [$PROJECT <folder>]")
        appendLine()
        appendLine("Commands:")
        Command.entries.forEach { appendLine("  ${it.word.padEnd(COLUMN)}${it.summary}") }
        appendLine()
        appendLine("Options:")
        appendLine("  ${PROJECT.padEnd(COLUMN)}The consuming project's folder, holding roto.json (default: here)")
        appendLine("  ${LIBRARY.padEnd(COLUMN)}The library folder holding the profiles")
        appendLine("  ${"".padEnd(COLUMN)}(default: \$$LIBRARY_VARIABLE, else the library chosen in the app)")
        appendLine("  ${PRESET.padEnd(COLUMN)}The built-in or saved preset to start from")
        append("  ${ID.padEnd(COLUMN)}The new project's id")
    }

    /** Whether [args] ask for the usage rather than a run. */
    fun wantsHelp(args: List<String>): Boolean = args.any { it == "-h" || it == "--help" }

    /** Reads [args]; [savedLibrary] is asked only when neither the option nor the variable names a library. */
    fun parse(
        args: List<String>,
        environment: Map<String, String>,
        workingDirectory: Path,
        savedLibrary: () -> Path?,
    ): Invocation {
        val options = mutableMapOf<String, String>()
        val positional = mutableListOf<String>()
        val remaining = ArrayDeque(args)
        while (remaining.isNotEmpty()) {
            val arg = remaining.removeFirst()
            when {
                !arg.startsWith("--") -> positional += arg
                '=' in arg -> option(arg.substringBefore('='), arg.substringAfter('='), options)
                else -> option(arg, remaining.removeFirstOrNull() ?: fail("$arg needs a ${values[arg]}"), options)
            }
        }
        val word = positional.getOrNull(0) ?: fail("No command given")
        val command = Command.entries.firstOrNull { it.word == word } ?: fail("Unknown command '$word'")
        options.keys.firstOrNull { it !in allowed.getValue(command) }
            ?.let { fail("$it is not an option of ${command.word}") }
        val argument = positional.getOrNull(1) ?: fail("No ${argumentName(command)} given to ${command.word}")
        if (positional.size > 2) fail("Unexpected '${positional[2]}' after the ${argumentName(command)}")
        return when (command) {
            Command.INIT -> init(argument, options, workingDirectory)
            Command.EXPORT -> export(argument, options, workingDirectory)
            else -> profile(
                command,
                argument,
                project(options, workingDirectory),
                library(options, environment, workingDirectory, savedLibrary),
            )
        }
    }

    private fun argumentName(command: Command) = when (command) {
        Command.INIT -> "folder"
        Command.EXPORT -> "bundle file"
        else -> "profile"
    }

    private fun profile(command: Command, profile: String, project: Path, library: Path): Invocation {
        if (!SafeName.isValid(profile)) fail("'$profile' is not a profile name")
        return ProfileInvocation(command, profile, project, library)
    }

    private fun library(
        options: Map<String, String>,
        environment: Map<String, String>,
        workingDirectory: Path,
        savedLibrary: () -> Path?,
    ): Path {
        val library = (options[LIBRARY] ?: environment[LIBRARY_VARIABLE]?.takeIf { it.isNotBlank() })
            ?.let { workingDirectory.resolve(it) }
            ?: savedLibrary()
            ?: fail("No library folder: pass $LIBRARY <folder>, set $LIBRARY_VARIABLE, or choose one in the app")
        return library.normalize()
    }

    private fun init(folder: String, options: Map<String, String>, workingDirectory: Path): Invocation {
        val preset = options[PRESET] ?: fail("init needs $PRESET <name>")
        val id = options[ID] ?: fail("init needs $ID <id>")
        if (!SafeName.isValid(id)) fail("'$id' is not a project id")
        return InitInvocation(workingDirectory.resolve(folder).normalize(), preset, id)
    }

    private fun export(file: String, options: Map<String, String>, workingDirectory: Path): Invocation {
        if (!file.endsWith(".${PresetBundle.EXTENSION}")) fail("'$file' does not end in .${PresetBundle.EXTENSION}")
        return ExportInvocation(workingDirectory.resolve(file).normalize(), project(options, workingDirectory))
    }

    private fun project(options: Map<String, String>, workingDirectory: Path): Path =
        workingDirectory.resolve(options[PROJECT] ?: "").normalize()

    private fun option(name: String, value: String, options: MutableMap<String, String>) {
        if (name !in values) fail("Unknown option $name")
        if (value.isBlank()) fail("$name needs a ${values[name]}")
        if (options.put(name, value) != null) fail("$name is given twice")
    }

    private fun fail(message: String): Nothing = throw UsageException(message)
}
