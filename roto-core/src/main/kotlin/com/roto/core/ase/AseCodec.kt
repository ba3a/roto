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

    fun read(path: Path): AseSprite = AseReader.decode(Files.readAllBytes(path))

    fun read(input: InputStream): AseSprite = AseReader.decode(input.readAllBytes())

    fun write(sprite: AseSprite, path: Path) {
        AtomicFile.write(path) { write(sprite, it) }
    }

    fun write(sprite: AseSprite, output: OutputStream) {
        output.write(AseWriter.encode(sprite))
    }
}
