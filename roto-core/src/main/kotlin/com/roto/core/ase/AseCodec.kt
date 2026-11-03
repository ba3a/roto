package com.roto.core.ase

import com.roto.core.io.AtomicFile
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads and writes [AseSprite]s as Aseprite `.ase` files, readable by Aseprite and LibreSprite.
 *
 * Only indexed-colour sprites are supported. The same sprite always encodes to the same bytes, and
 * reading what was written gives back an equal sprite. Reading also accepts the files those editors
 * save after an artist has worked on them; anything outside the [AseSprite] subset fails with an
 * [AseFormatException] that names the feature.
 */
object AseCodec {

    /** Reads the sprite saved at [path]. */
    fun read(path: Path): AseSprite = AseReader.decode(Files.readAllBytes(path))

    /** Reads a sprite from [input], which is read to its end but not closed. */
    fun read(input: InputStream): AseSprite = AseReader.decode(input.readAllBytes())

    /** Writes [sprite] to [path] through [AtomicFile], so a crash never leaves a half-written file. */
    fun write(sprite: AseSprite, path: Path) {
        AtomicFile.write(path) { write(sprite, it) }
    }

    /** Writes [sprite] to [output], which is not closed. */
    fun write(sprite: AseSprite, output: OutputStream) {
        output.write(AseWriter.encode(sprite))
    }
}
