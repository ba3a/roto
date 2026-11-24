package com.roto.core.model

import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import com.roto.core.publish.ClipManifest
import com.roto.core.publish.ClipManifestCodec
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A folder of PNG frames, sorted by file name, as one animation. A `clip.json` beside them (Roto's own
 * [ClipManifest]) gives each frame's duration and the loop flag; without one every frame lasts
 * [defaultDurationMs] and the loop flag is [defaultLoop]. A published clip folder works as it is.
 *
 * @param folder the folder holding the PNGs
 * @param canvas the project canvas; every frame must match its size
 * @param defaultDurationMs the artist's one duration for all frames when there is no `clip.json`
 * @param defaultLoop the artist's loop choice when there is no `clip.json`
 */
class PngFolderModelSource(
    private val folder: Path,
    private val canvas: Canvas,
    private val defaultDurationMs: Int,
    private val defaultLoop: Boolean,
) : ModelSource {

    init {
        require(defaultDurationMs >= 1) { "A frame lasts at least 1 ms, got $defaultDurationMs" }
    }

    private val files: List<Path> by lazy {
        val found = try {
            folder.listDirectoryEntries("*.png").filter { it.isRegularFile() }.sortedBy { it.name }
        } catch (e: IOException) {
            throw ModelException("$folder: ${e.message ?: e.javaClass.simpleName}", e)
        }
        if (found.isEmpty()) throw ModelException("$folder: no PNG frames found")
        found
    }

    private val manifest: ClipManifest? by lazy {
        val file = folder.resolve(MANIFEST_NAME)
        if (!file.isRegularFile()) {
            null
        } else {
            try {
                ClipManifestCodec.read(file).also { manifest ->
                    if (manifest.frames.size != files.size) {
                        throw ModelException(
                            "$file: lists ${manifest.frames.size} frames but the folder holds ${files.size} PNGs",
                        )
                    }
                }
            } catch (e: ModelException) {
                throw e
            } catch (e: IOException) {
                throw ModelException(e.message ?: "$file: unreadable", e)
            }
        }
    }

    /** One row: the folder, its name read as `<action>_<facing>` when it does. A `clip.json` that disagrees with the PNGs fails here. */
    override fun rows(): List<ModelRow> {
        manifest
        val name = folder.fileName?.toString() ?: folder.toString()
        val (action, facing) = guessActionAndFacing(name)
        return listOf(ModelRow(name, action, facing, files.size))
    }

    /** The PNGs in name order; one of another size than the canvas stops the import. */
    override fun frames(row: ModelRow): List<Raster> =
        files.map { file ->
            val raster = try {
                PngCodec.read(file)
            } catch (e: IOException) {
                throw ModelException("$file: ${e.message ?: e.javaClass.simpleName}", e)
            }
            if (raster.width != canvas.width || raster.height != canvas.height) {
                throw ModelException(
                    "${file.name} is ${raster.width}x${raster.height}, " +
                        "but the canvas is ${canvas.width}x${canvas.height}",
                )
            }
            raster
        }

    /** The manifest's durations, or the artist's one duration for every frame. */
    override fun durations(row: ModelRow): List<Int> =
        manifest?.frames?.map { it.durationMs } ?: List(files.size) { defaultDurationMs }

    /** The manifest's loop flag, or the artist's choice. */
    override fun loop(row: ModelRow): Boolean = manifest?.loop ?: defaultLoop
}

private const val MANIFEST_NAME = "clip.json"
