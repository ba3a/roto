package com.roto.core.cache

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Stage outputs stored on disk under [folder] (a profile's `cache/`), one entry folder per
 * [CacheKey] at `<stage>/<hash>/`.
 *
 * An entry is filled in a temporary folder beside it and moved into place in one step, so an entry
 * folder that exists is always complete: a crash or an exception mid-fill leaves no entry behind, and
 * two processes filling the same key at once both end up reading one complete entry. An entry whose
 * content no longer reads back is discarded and computed again — everything here is regenerable from
 * sources plus parameters, which is also why [clear] is always safe.
 */
class StageCache(folder: Path) {

    val folder: Path = folder.toAbsolutePath().normalize()

    /** Whether [key]'s output is stored. */
    fun contains(key: CacheKey): Boolean = Files.isDirectory(entryFolder(key))

    /** [key]'s output: read through [codec] when stored, otherwise [compute]d, stored, and returned. */
    fun <T : Any> get(key: CacheKey, codec: CacheCodec<T>, compute: () -> T): T {
        readStored(key, codec::read)?.let { return it }
        val value = compute()
        store(entryFolder(key)) { codec.write(value, it) }
        return value
    }

    /**
     * [key]'s output, [read] from its entry folder after [fill]ing it unless already stored. For a stage
     * whose output something else writes as files, such as frames from an external process: [fill]
     * gets an empty folder to write into, and its content becomes the entry.
     */
    fun <T : Any> getOrFill(key: CacheKey, read: (entry: Path) -> T, fill: (entry: Path) -> Unit): T {
        readStored(key, read)?.let { return it }
        val entry = entryFolder(key)
        store(entry, fill)
        return read(entry)
    }

    /** Deletes every stored output. */
    fun clear() {
        folder.toFile().deleteRecursively()
    }

    private fun entryFolder(key: CacheKey): Path = folder.resolve(key.stage).resolve(key.hash)

    /** [key]'s stored output, or null when there is none. */
    private fun <T : Any> readStored(key: CacheKey, read: (entry: Path) -> T): T? {
        val entry = entryFolder(key)
        if (!Files.isDirectory(entry)) return null
        return try {
            read(entry)
        } catch (ignored: IOException) {
            // A damaged entry is only a cache miss: drop it so the output is computed again.
            entry.toFile().deleteRecursively()
            null
        }
    }

    private fun store(entry: Path, fill: (Path) -> Unit) {
        val stageFolder = entry.parent
        Files.createDirectories(stageFolder)
        // The leading dot keeps a temporary folder from ever looking like an entry, which is named by a hash.
        val temporary = Files.createTempDirectory(stageFolder, ".${entry.fileName}.")
        try {
            fill(temporary)
            try {
                Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: IOException) {
                // Another writer stored the same key first; its entry holds the same output.
                if (!Files.isDirectory(entry)) throw e
            }
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }
}
