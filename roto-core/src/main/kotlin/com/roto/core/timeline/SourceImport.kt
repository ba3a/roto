package com.roto.core.timeline

import com.roto.core.io.AtomicFile
import com.roto.core.profile.ProfileFolder
import com.roto.core.template.RelativePath
import java.nio.file.Files
import java.nio.file.Path

/**
 * Brings a video into a profile's `sources/` folder, which holds the originals Roto never modifies.
 * The file is copied, whole or not at all; the original stays where it was. A file already in the
 * folder, or an identical copy of one, is not copied again; a different file of the same name gets a
 * numbered name, so nothing there is ever replaced.
 */
object SourceImport {

    /** [file], in [folder]'s sources; returns its path there, as a clip's `source` holds it. */
    fun import(file: Path, folder: ProfileFolder): String {
        val sources = folder.sourcesFolder.toAbsolutePath().normalize()
        val absolute = file.toAbsolutePath().normalize()
        if (absolute.startsWith(sources)) return sources.relativize(absolute).joinToString("/")
        val name = RelativePath.safeSegment(checkNotNull(absolute.fileName).toString())
        val target = candidates(name).map { sources.resolve(it) }.first { !Files.exists(it) || same(it, absolute) }
        if (!Files.exists(target)) AtomicFile.write(target) { Files.copy(absolute, it) }
        return target.fileName.toString()
    }

    /** [name], then `stem-2.ext`, `stem-3.ext`, … */
    private fun candidates(name: String): Sequence<String> {
        val stem = name.substringBeforeLast('.')
        val extension = name.removePrefix(stem)
        return sequenceOf(name) + generateSequence(2) { it + 1 }.map { "$stem-$it$extension" }
    }

    private fun same(first: Path, second: Path): Boolean = Files.mismatch(first, second) == -1L
}
