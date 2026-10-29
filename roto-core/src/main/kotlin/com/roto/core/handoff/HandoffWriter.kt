package com.roto.core.handoff

import com.roto.core.ase.AseCodec
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a clip's handoff `.ase`, keeping the artist's layers of the file already there by
 * [HandoffMerge]. An existing file that cannot be read — damaged, or saved in a form Roto does not
 * read — fails the write with its reason, and is left exactly as it was: a file that may hold the
 * artist's painting is never overwritten unread.
 */
object HandoffWriter {

    fun write(file: Path, render: HandoffRender) {
        val existing = if (Files.exists(file)) AseCodec.read(file) else null
        val sprite = try {
            HandoffMerge.merge(existing, render)
        } catch (e: IllegalArgumentException) {
            throw HandoffException("$file: ${e.message}", e)
        }
        AseCodec.write(sprite, file)
    }
}
