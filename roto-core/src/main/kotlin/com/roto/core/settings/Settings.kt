package com.roto.core.settings

import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * The artist's own Roto settings on this machine: the [library] folder that holds every profile,
 * chosen once, the [project] folder last opened, and the [editor] program handoffs open in, if they
 * chose one over the system's default. All are absolute paths: they describe this machine, not any
 * consuming project, so the rule that project paths are relative does not apply.
 */
@Serializable
data class Settings(val library: String? = null, val project: String? = null, val editor: String? = null) {
    init {
        library?.let { requireAbsolute(it, "library") }
        project?.let { requireAbsolute(it, "project") }
        editor?.let { requireAbsolute(it, "editor") }
    }

    /** The library folder, or null until one is chosen. */
    val libraryFolder: Path? get() = library?.let { Path.of(it) }

    /** The project folder last opened, or null. */
    val projectFolder: Path? get() = project?.let { Path.of(it) }

    /** The editor program chosen, or null for the system's default. */
    val editorProgram: Path? get() = editor?.let { Path.of(it) }

    fun withLibrary(folder: Path): Settings = copy(library = folder.toAbsolutePath().normalize().toString())

    fun withProject(folder: Path): Settings = copy(project = folder.toAbsolutePath().normalize().toString())

    fun withEditor(program: Path): Settings = copy(editor = program.toAbsolutePath().normalize().toString())
}

private fun requireAbsolute(path: String, what: String) {
    require(path.isNotBlank() && Path.of(path).isAbsolute) { "$what must be an absolute path, got '$path'" }
}
