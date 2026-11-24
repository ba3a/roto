package com.roto.core.model

import com.roto.core.image.PngCodec
import com.roto.core.profile.ProfileFolder
import com.roto.core.template.SafeName
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** How [ModelRepository.save] performs one file write: runs [write], which creates [file]. A seam for tests. */
fun interface ModelFileWriter {
    /** Runs [write], which writes [file]. */
    fun write(file: Path, write: () -> Unit)
}

private val DirectWriter = ModelFileWriter { _, write -> write() }

/**
 * The models of one profile, under its `models/` folder: one `<action>_<facing>/` folder each, holding
 * `model.json` and the frames `000.png`, `001.png`, and so on. A folder that cannot be read fails with a
 * [ModelException] naming the file.
 */
class ModelRepository(private val profileFolder: ProfileFolder) {

    /** The keys of every model that has a `model.json`, in sorted order. */
    fun keys(): List<String> {
        val models = profileFolder.modelsFolder
        if (!models.isDirectory()) return emptyList()
        return models.listDirectoryEntries()
            .filter { it.isDirectory() && SafeName.isValid(it.name) && it.resolve(ModelCodec.fileName).isRegularFile() }
            .map { it.name }
            .sorted()
    }

    /** Whether a model called [key] exists. */
    fun exists(key: String): Boolean = profileFolder.modelFolder(key).resolve(ModelCodec.fileName).isRegularFile()

    /** Loads the model called [key]; a missing or unreadable file fails with a [ModelException] naming it. */
    fun load(key: String): ModelClip {
        val folder = profileFolder.modelFolder(key)
        val manifest = guarded(folder.resolve(ModelCodec.fileName)) { ModelCodec.read(it) }
        if (manifest.key != key) {
            throw ModelException("${folder.resolve(ModelCodec.fileName)}: holds model ${manifest.key}, not $key")
        }
        val frames = manifest.durations.mapIndexed { index, duration ->
            val file = folder.resolve(frameName(index))
            guarded(file) { ModelFrame(PngCodec.read(it), duration) }
        }
        return guarded(folder) {
            ModelClip(manifest.action, manifest.facing, frames, manifest.loop, manifest.importedFrom)
        }
    }

    /**
     * Writes [model] under its key: the frames first, `model.json` last, so a folder with a `model.json`
     * is always complete. Replaces a model of the same key, then removes frames left over from it. Each
     * file write goes through [writer].
     */
    fun save(model: ModelClip, writer: ModelFileWriter = DirectWriter) {
        require(model.frames.size <= MAX_FRAMES) { "A model has at most $MAX_FRAMES frames, got ${model.frames.size}" }
        val folder = profileFolder.modelFolder(model.key)
        Files.createDirectories(folder)
        model.frames.forEachIndexed { index, frame ->
            val file = folder.resolve(frameName(index))
            writer.write(file) { PngCodec.write(frame.raster, file) }
        }
        val manifestFile = folder.resolve(ModelCodec.fileName)
        writer.write(manifestFile) { ModelCodec.write(model.manifest(), manifestFile) }
        folder.listDirectoryEntries().filter { isFrameFile(it) && it.name > frameName(model.frames.size - 1) }
            .forEach { Files.delete(it) }
    }

    /** Deletes the model called [key], folder and all; does nothing when there is none. */
    fun remove(key: String) {
        profileFolder.modelFolder(key).toFile().deleteRecursively()
    }

    /**
     * The content hash of the model called [key]: SHA-256, in hex, of `model.json` and then every frame
     * file in order. Any changed pixel or duration changes it; it is the cache-key input for everything
     * derived from the model.
     */
    fun contentHash(key: String): String {
        val folder = profileFolder.modelFolder(key)
        val manifestFile = folder.resolve(ModelCodec.fileName)
        val manifest = guarded(manifestFile) { ModelCodec.read(it) }
        val digest = MessageDigest.getInstance(ALGORITHM)
        val files = listOf(manifestFile) + manifest.durations.indices.map { folder.resolve(frameName(it)) }
        files.forEach { file ->
            val bytes = guarded(file) { Files.readAllBytes(it) }
            digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(SEPARATOR)
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun <T> guarded(file: Path, body: (Path) -> T): T =
        try {
            body(file)
        } catch (e: ModelException) {
            throw e
        } catch (e: IOException) {
            throw ModelException("$file: ${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: IllegalArgumentException) {
            throw ModelException("$file: ${e.message}", e)
        }

    private fun isFrameFile(path: Path): Boolean = path.name.matches(FRAME_FILE)
}

private const val ALGORITHM = "SHA-256"
private const val MAX_FRAMES = 1000
private const val FRAME_DIGITS = 3
private val SEPARATOR = byteArrayOf(0)
private val FRAME_FILE = Regex("\\d{$FRAME_DIGITS,}\\.png")

private fun frameName(index: Int): String = "%0${FRAME_DIGITS}d.png".format(index)
