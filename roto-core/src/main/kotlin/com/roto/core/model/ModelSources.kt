package com.roto.core.model

import com.roto.core.project.ProjectConfig
import com.roto.core.template.Template
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** What a path the artist picked holds. */
enum class ModelSourceKind {
    /** A folder of PNG frames. */
    PNG_FOLDER,

    /** An `.ase` or `.aseprite` file. */
    ASE,

    /** A libGDX `.atlas` file. */
    ATLAS,
}

/** Picks and builds the [ModelSource] for a path: the artist chooses a file or folder, never a format. */
object ModelSources {

    /** The kind of source at [path]; a [ModelException] when it is none of them or does not exist. */
    fun detect(path: Path): ModelSourceKind {
        val name = path.fileName.toString().lowercase()
        return when {
            Files.isDirectory(path) -> ModelSourceKind.PNG_FOLDER
            !Files.isRegularFile(path) -> throw NoSuchFileException(path.toString())
            name.endsWith(".ase") || name.endsWith(".aseprite") -> ModelSourceKind.ASE
            name.endsWith(".atlas") -> ModelSourceKind.ATLAS
            else -> throw ModelException("$path is not a PNG folder, .ase, .aseprite or .atlas file")
        }
    }

    /**
     * The source at [path] for [config]. [durationMs] is every frame's duration where the source gives
     * none, and [pattern] the atlas name pattern (null: the one the project's output template gives).
     * Every animation loops until the artist says otherwise. A bad [pattern] is a [ModelException].
     */
    fun open(path: Path, config: ProjectConfig, durationMs: Int, pattern: String? = null): ModelSource =
        try {
            when (detect(path)) {
                ModelSourceKind.PNG_FOLDER -> PngFolderModelSource(path, config.canvas, durationMs, LOOPS)
                ModelSourceKind.ASE -> AseModelSource(path, config.canvas, LOOPS)
                ModelSourceKind.ATLAS -> AtlasModelSource(
                    path,
                    config.canvas,
                    config.actions,
                    config.facings,
                    Template(pattern ?: defaultAtlasPattern(config.output)),
                    config.actions.associateWith { durationMs },
                    config.actions.toSet(),
                )
            }
        } catch (e: IllegalArgumentException) {
            throw ModelException(e.message ?: "Bad model source", e)
        }
}

private const val LOOPS = true
