package com.roto.core.settings

import kotlinx.serialization.Serializable
import java.nio.file.Path

/** How many projects [Settings.recent] keeps. */
const val MAX_RECENT_PROJECTS = 10

/**
 * A project the artist opened before, with the library it was worked on in, so reopening it finds the
 * same assets.
 *
 * @property project the project folder as an absolute path
 * @property library the library folder as an absolute path, or null when none was chosen then
 */
@Serializable
data class RecentProject(val project: String, val library: String? = null) {
    init {
        requireAbsolute(project, "recent project")
        library?.let { requireAbsolute(it, "recent library") }
    }
}

/**
 * The artist's own Roto settings on this machine: the [library] folder that holds every profile,
 * the [project] folder last opened, the [editor] program handoffs open in, if they chose one over the
 * system's default, the [folders] each kind of file chooser last used, and the [recent] projects.
 * All are absolute paths: they describe this machine, not any consuming project, so the rule that
 * project paths are relative does not apply.
 *
 * @property library the library folder as an absolute path, or null until chosen
 * @property project the project folder last opened, or null
 * @property editor the editor program, or null for the system's default
 * @property folders the last path chosen in each kind of file chooser, by the kind's name
 * @property recent the projects opened before, newest first, at most [MAX_RECENT_PROJECTS]
 */
@Serializable
data class Settings(
    val library: String? = null,
    val project: String? = null,
    val editor: String? = null,
    val folders: Map<String, String> = emptyMap(),
    val recent: List<RecentProject> = emptyList(),
) {
    init {
        library?.let { requireAbsolute(it, "library") }
        project?.let { requireAbsolute(it, "project") }
        editor?.let { requireAbsolute(it, "editor") }
        folders.forEach { (kind, path) -> requireAbsolute(path, "folder of $kind") }
        require(recent.size <= MAX_RECENT_PROJECTS) { "at most $MAX_RECENT_PROJECTS recent projects" }
        require(recent.map { it.project }.toSet().size == recent.size) { "a project is listed once in recent" }
    }

    /** The library folder, or null until one is chosen. */
    val libraryFolder: Path? get() = library?.let { Path.of(it) }

    /** The project folder last opened, or null. */
    val projectFolder: Path? get() = project?.let { Path.of(it) }

    /** The editor program chosen, or null for the system's default. */
    val editorProgram: Path? get() = editor?.let { Path.of(it) }

    /** The last path chosen in the kind of file chooser called [kind], or null when there is none. */
    fun folder(kind: String): Path? = folders[kind]?.let { Path.of(it) }

    /** These settings with the library [folder] chosen; the open project is remembered with it. */
    fun withLibrary(folder: Path): Settings {
        val chosen = folder.toAbsolutePath().normalize().toString()
        val updated = recent.map { if (it.project == project) it.copy(library = chosen) else it }
        return copy(library = chosen, recent = updated)
    }

    /**
     * These settings with the project [folder] last opened and first in [recent]. A project opened
     * before brings back the library it was worked on in; a new one keeps the current library.
     */
    fun withProject(folder: Path): Settings {
        val opened = folder.toAbsolutePath().normalize().toString()
        val chosen = recent.firstOrNull { it.project == opened }?.library ?: library
        val others = recent.filter { it.project != opened }
        return copy(
            project = opened,
            library = chosen,
            recent = (listOf(RecentProject(opened, chosen)) + others).take(MAX_RECENT_PROJECTS),
        )
    }

    /** These settings with [path] remembered as the last one chosen in the kind of file chooser called [kind]. */
    fun withFolder(kind: String, path: Path): Settings =
        copy(folders = (folders + (kind to path.toAbsolutePath().normalize().toString())).toSortedMap().toMap())

    /** These settings with the editor [program] chosen. */
    fun withEditor(program: Path): Settings = copy(editor = program.toAbsolutePath().normalize().toString())
}

private fun requireAbsolute(path: String, what: String) {
    require(path.isNotBlank() && Path.of(path).isAbsolute) { "$what must be an absolute path, got '$path'" }
}
