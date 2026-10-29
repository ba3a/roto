package com.roto.core.template

import java.nio.file.Path

/**
 * The one rule for paths a consuming project writes in `roto.json`: forward slashes, relative to the
 * project root, and portable to every desktop file system. Each segment is non-empty, is not `.` or
 * `..`, and avoids the characters Windows forbids in file names — so a path that validates on Linux
 * also works on Windows, and no path can climb out of the project root.
 */
object RelativePath {

    private const val FORBIDDEN = "\\<>:\"|?*"

    /** Throws unless [path] is a valid relative path; [what] names it in the message. */
    fun validate(path: String, what: String) {
        require(path.isNotEmpty()) { "$what must not be empty" }
        path.split('/').forEach { segment -> validateSegment(segment, what, path) }
    }

    /** [path], validated, resolved against [root]. */
    fun resolve(root: Path, path: String, what: String): Path {
        validate(path, what)
        return root.resolve(path)
    }

    /** Throws if [text] contains a character a single path segment may not hold, `/` included. */
    internal fun validateSegmentCharacters(text: String, what: String) {
        text.firstOrNull { it == '/' || isForbidden(it) }?.let { bad ->
            throw IllegalArgumentException(
                "$what '$text' contains '${printable(bad)}', which is not allowed in a file name",
            )
        }
    }

    private fun validateSegment(segment: String, what: String, path: String) {
        require(segment.isNotEmpty()) {
            "$what '$path' must be relative, with no empty segment (no leading, trailing or double '/')"
        }
        require(segment != "." && segment != "..") { "$what '$path' must not contain '.' or '..' segments" }
        segment.firstOrNull(::isForbidden)?.let { bad ->
            throw IllegalArgumentException(
                "$what '$path' contains '${printable(bad)}', which is not allowed in a path",
            )
        }
    }

    private fun isForbidden(char: Char): Boolean = char < ' ' || char in FORBIDDEN

    private fun printable(char: Char): String = if (char < ' ') "\\u%04X".format(char.code) else char.toString()
}
