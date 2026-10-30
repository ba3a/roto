package com.roto.cli

import com.roto.core.template.SafeName
import java.nio.file.Path

/** What `roto` can be asked to do with a profile. */
internal enum class Command(val word: String, val summary: String) {
    RENDER("render", "Render the handoff of every clip in a profile"),
    PUBLISH("publish", "Publish the finished art of every clip in a profile"),
}

/** One run of `roto`: [command] on the profile [profile] of the [project] folder, stored in [library]. */
internal data class Invocation(val command: Command, val profile: String, val project: Path, val library: Path)

/** A command line `roto` cannot make sense of; its message says what is wrong. */
internal class UsageException(message: String) : Exception(message)

/**
 * Reads `roto <command> <profile> [--project <folder>] [--library <folder>]`. The project defaults to
 * the working folder; the library to the [LIBRARY_VARIABLE] environment variable. Relative folders
 * are taken from the working folder. An option's value may follow it or be joined with `=`.
 */
internal object Arguments {

    const val LIBRARY_VARIABLE = "ROTO_LIBRARY"
    private const val PROJECT = "--project"
    private const val LIBRARY = "--library"
    private const val COLUMN = 12

    val usage: String = buildString {
        appendLine("Usage: roto <command> <profile> [$PROJECT <folder>] [$LIBRARY <folder>]")
        appendLine()
        appendLine("Commands:")
        Command.entries.forEach { appendLine("  ${it.word.padEnd(COLUMN)}${it.summary}") }
        appendLine()
        appendLine("Options:")
        appendLine("  ${PROJECT.padEnd(COLUMN)}The consuming project's folder, holding roto.json (default: here)")
        append("  ${LIBRARY.padEnd(COLUMN)}The library folder holding the profiles (default: \$$LIBRARY_VARIABLE)")
    }

    /** Whether [args] ask for the usage rather than a run. */
    fun wantsHelp(args: List<String>): Boolean = args.any { it == "-h" || it == "--help" }

    fun parse(args: List<String>, environment: Map<String, String>, workingDirectory: Path): Invocation {
        val options = mutableMapOf<String, String>()
        val positional = mutableListOf<String>()
        val remaining = ArrayDeque(args)
        while (remaining.isNotEmpty()) {
            val arg = remaining.removeFirst()
            when {
                !arg.startsWith("--") -> positional += arg
                '=' in arg -> option(arg.substringBefore('='), arg.substringAfter('='), options)
                else -> option(arg, remaining.removeFirstOrNull() ?: fail("$arg needs a folder"), options)
            }
        }
        val (command, profile) = commandAndProfile(positional)
        val library = options[LIBRARY] ?: environment[LIBRARY_VARIABLE]?.takeIf { it.isNotBlank() }
            ?: fail("No library folder: pass $LIBRARY <folder> or set $LIBRARY_VARIABLE")
        return Invocation(
            command,
            profile,
            project = workingDirectory.resolve(options[PROJECT] ?: "").normalize(),
            library = workingDirectory.resolve(library).normalize(),
        )
    }

    private fun option(name: String, value: String, options: MutableMap<String, String>) {
        if (name != PROJECT && name != LIBRARY) fail("Unknown option $name")
        if (value.isBlank()) fail("$name needs a folder")
        if (options.put(name, value) != null) fail("$name is given twice")
    }

    private fun commandAndProfile(positional: List<String>): Pair<Command, String> {
        val word = positional.getOrNull(0) ?: fail("No command given")
        val command = Command.entries.firstOrNull { it.word == word } ?: fail("Unknown command '$word'")
        val profile = positional.getOrNull(1) ?: fail("No profile given to ${command.word}")
        if (positional.size > 2) fail("Unexpected '${positional[2]}' after the profile")
        if (!SafeName.isValid(profile)) fail("'$profile' is not a profile name")
        return command to profile
    }

    private fun fail(message: String): Nothing = throw UsageException(message)
}
