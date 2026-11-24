package com.roto.core.preset

import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.ProjectPalette
import com.roto.core.palette.RampSetCodec
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A scaffold that could not write a project.
 *
 * @property written the files written before it stopped, in the order written; none is `roto.json`
 * unless the failure came after it
 */
class ScaffoldException(message: String, val written: List<Path> = emptyList(), cause: Throwable? = null) :
    IOException(message, cause)

/** How [ProjectScaffolder] performs one file write: runs [write], which creates [file]. A seam for tests. */
fun interface ScaffoldWriter {
    /** Runs [write], which writes [file]. */
    fun write(file: Path, write: () -> Unit)
}

/** The writer that just runs the write. */
private val DirectWriter = ScaffoldWriter { _, write -> write() }

/**
 * Writes a new project from a [ProjectPreset]: `roto/palette.hex`, `roto/ramps.json`, then `roto.json`
 * last, so a folder holding `roto.json` is always a complete project. Writes nothing and overwrites
 * nothing: if any of the three files exists it stops first.
 */
object ProjectScaffolder {

    /**
     * Writes [preset] as project [id] under [folder] and returns it opened. [id] must follow
     * [SafeName]. A failed write is a [ScaffoldException] listing the files already written; a failure
     * to load the result propagates unchanged. Each write goes through [writer].
     */
    fun scaffold(
        preset: ProjectPreset,
        id: String,
        folder: Path,
        writer: ScaffoldWriter = DirectWriter,
    ): Project {
        SafeName.validate(id, "Project id")
        val config = preset.toConfig(id)
        val palette = RelativePath.resolve(folder, config.palette, "palette")
        val ramps = RelativePath.resolve(folder, config.ramps, "ramps")
        val configFile = folder.resolve(ProjectConfigCodec.fileName)
        listOf(palette, ramps, configFile).firstOrNull { Files.exists(it) }?.let {
            throw ScaffoldException("$it already exists; nothing was written")
        }

        val written = mutableListOf<Path>()
        fun write(file: Path, body: () -> Unit) {
            try {
                writer.write(file, body)
            } catch (e: IOException) {
                throw failure(file, written, e)
            }
            written.add(file)
        }
        write(palette) { PaletteCodec.write(preset.palette, palette) }
        write(ramps) { RampSetCodec.write(preset.ramps, ramps) }
        write(configFile) { ProjectConfigCodec.write(config, configFile) }

        val project = Project.open(folder)
        ProjectPalette.load(project)
        return project
    }

    private fun failure(file: Path, written: List<Path>, cause: IOException): ScaffoldException {
        val done = if (written.isEmpty()) "no files written" else "already written: ${written.joinToString()}"
        return ScaffoldException("Could not write $file (${cause.message}); $done", written.toList(), cause)
    }
}
