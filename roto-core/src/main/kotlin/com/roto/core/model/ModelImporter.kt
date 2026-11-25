package com.roto.core.model

import com.roto.core.profile.ProfileFolder
import com.roto.core.project.ProjectConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * One row of a [ModelSource] the artist confirmed.
 *
 * @property row the animation the source found
 * @property action the confirmed action; must be one of the project's actions
 * @property facing the confirmed facing; must be one of the project's facings
 */
data class ConfirmedRow(val row: ModelRow, val action: String, val facing: String) {
    /** `<action>_<facing>`: the folder the model will have. */
    val key: String get() = "${action}_$facing"
}

/**
 * Turns a [ModelSource] and the artist's confirmed rows into model folders under a profile. All rows are
 * checked first, then staged in a temporary folder with `model.json` written last in each, then moved
 * into place; a failure leaves the profile as it was. A key that already exists is refused unless
 * `replace` is true. The same source always gives byte-identical folders.
 *
 * @property profileFolder the profile the models go into
 * @property config the project, which names the allowed actions and facings and the canvas
 */
class ModelImporter(private val profileFolder: ProfileFolder, private val config: ProjectConfig) {

    /**
     * Imports [rows] from [source] and returns the models' keys in row order. [origin] is recorded as
     * where they came from. Each staged file write goes through [writer].
     */
    fun import(
        source: ModelSource,
        rows: List<ConfirmedRow>,
        origin: String,
        replace: Boolean,
        writer: ModelFileWriter = ModelFileWriter { _, write -> write() },
    ): List<String> {
        guarded { validate(rows, replace) }
        Files.createDirectories(profileFolder.path)
        val stage = Files.createTempDirectory(profileFolder.path, STAGE_PREFIX)
        try {
            guarded { stageAndPlace(ProfileFolder(stage), source, rows, origin, writer) }
        } finally {
            stage.toFile().deleteRecursively()
        }
        return rows.map { it.key }
    }

    private fun validate(rows: List<ConfirmedRow>, replace: Boolean) {
        require(rows.isNotEmpty()) { "Nothing to import: no rows confirmed" }
        val repository = ModelRepository(profileFolder)
        val seen = mutableSetOf<String>()
        for (confirmed in rows) {
            require(confirmed.action in config.actions) {
                "Unknown action '${confirmed.action}'; the project has ${config.actions}"
            }
            require(confirmed.facing in config.facings) {
                "Unknown facing '${confirmed.facing}'; the project has ${config.facings}"
            }
            require(seen.add(confirmed.key)) { "Two rows would become model ${confirmed.key}" }
            require(replace || !repository.exists(confirmed.key)) {
                "Model ${confirmed.key} already exists; replace it to import over it"
            }
        }
    }

    private fun load(source: ModelSource, confirmed: ConfirmedRow, origin: String): ModelClip {
        val frames = source.frames(confirmed.row)
        val durations = source.durations(confirmed.row)
        require(frames.size == durations.size) {
            "Row ${confirmed.row.id} has ${frames.size} frames but ${durations.size} durations"
        }
        frames.forEach {
            require(it.width == config.canvas.width && it.height == config.canvas.height) {
                "Row ${confirmed.row.id} has a ${it.width}x${it.height} frame; " +
                    "the canvas is ${config.canvas.width}x${config.canvas.height}"
            }
        }
        return ModelClip(
            confirmed.action,
            confirmed.facing,
            frames.zip(durations) { raster, duration -> ModelFrame(raster, duration) },
            source.loop(confirmed.row),
            origin,
        )
    }

    private fun stageAndPlace(
        staged: ProfileFolder,
        source: ModelSource,
        rows: List<ConfirmedRow>,
        origin: String,
        writer: ModelFileWriter,
    ) {
        val repository = ModelRepository(staged)
        rows.forEach { repository.save(load(source, it, origin), writer) }
        rows.forEach { place(staged.modelFolder(it.key), profileFolder.modelFolder(it.key)) }
    }

    private fun guarded(body: () -> Unit) {
        try {
            body()
        } catch (e: IOException) {
            throw e as? ModelException ?: ModelException("Could not import models: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ModelException(e.message ?: "Could not import models", e)
        }
    }

    private fun place(from: Path, to: Path) {
        Files.createDirectories(to.parent)
        if (Files.exists(to)) to.toFile().deleteRecursively()
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
    }
}

private const val STAGE_PREFIX = ".import-"
