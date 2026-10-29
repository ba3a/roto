package com.roto.core.profile

import com.roto.core.template.RelativePath
import java.nio.file.Path

/**
 * The layout of one profile's folder in the library:
 *
 * - `profile.json` — the [Profile];
 * - `sources/` — the original videos and photos, never modified;
 * - `cache/` — stage outputs, always regenerable from sources plus parameters, safe to delete;
 * - `handoff/` — one `<action>_<facing>.ase` per clip, the file the artist paints in.
 */
class ProfileFolder(val path: Path) {

    val profileFile: Path get() = path.resolve(ProfileCodec.fileName)

    val sourcesFolder: Path get() = path.resolve(SOURCES)

    val cacheFolder: Path get() = path.resolve(CACHE)

    val handoffFolder: Path get() = path.resolve(HANDOFF)

    /** The video or photo [clip] was cut from. */
    fun sourceFile(clip: Clip): Path = RelativePath.resolve(sourcesFolder, clip.source, "source")

    /** The `.ase` the artist paints [clip] in. */
    fun handoffFile(clip: Clip): Path = handoffFolder.resolve("${clip.id}.ase")

    private companion object {
        const val SOURCES = "sources"
        const val CACHE = "cache"
        const val HANDOFF = "handoff"
    }
}
