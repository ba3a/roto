package com.roto.core.ase

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes [AseSprite]s as Aseprite `.ase` files, readable by Aseprite and LibreSprite.
 *
 * Only indexed-colour sprites are supported. The same sprite always encodes to the same bytes.
 */
object AseCodec {

    fun write(sprite: AseSprite, path: Path) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { write(sprite, it) }
    }

    fun write(sprite: AseSprite, output: OutputStream) {
        output.write(AseWriter.encode(sprite))
    }
}
