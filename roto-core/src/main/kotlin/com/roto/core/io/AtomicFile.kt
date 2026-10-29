package com.roto.core.io

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes a file so that it is either fully replaced or left as it was: the content goes to a
 * temporary file beside the target, which is then moved over it in one step. A crash or an exception
 * halfway through never leaves a truncated `profile.json` or `.ase` behind. Every codec's
 * `write(value, Path)` goes through here.
 */
object AtomicFile {

    /** Creates [path]'s parent folders, then replaces [path] with what [body] writes. */
    fun write(path: Path, body: (OutputStream) -> Unit) {
        val target = path.toAbsolutePath()
        val folder = target.parent
        Files.createDirectories(folder)
        val temporary = Files.createTempFile(folder, "${target.fileName}.", ".tmp")
        try {
            Files.newOutputStream(temporary).use(body)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
