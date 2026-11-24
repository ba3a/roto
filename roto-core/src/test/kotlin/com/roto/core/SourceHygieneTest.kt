package com.roto.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks out invisible characters in the module's sources and text resources. An editing tool can turn an
 * escape for U+FEFF into the literal byte-order mark, which compiles and reads exactly like the escape but
 * means something else — a test can then pass for the wrong reason. Write such characters as
 * `Char(0xFEFF)`.
 */
class SourceHygieneTest {

    private val textExtensions = setOf("kt", "json", "hex", "txt")

    private val invisible = mapOf(
        Char(0xFEFF) to "byte-order mark",
        Char(0x200B) to "zero-width space",
        Char(0x200C) to "zero-width non-joiner",
        Char(0x200D) to "zero-width joiner",
        Char(0x00A0) to "no-break space",
    )

    @Test
    fun `no source file holds an invisible character`() {
        val roots = listOf("src/main/kotlin", "src/test/kotlin", "src/main/resources").map { Path.of(it) }
        val sources = roots.flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { it.isRegularFile() && it.extension in textExtensions }.toList()
            }
        }

        val findings = sources.sorted().flatMap { file ->
            file.readText().lines().withIndex().flatMap { (index, line) ->
                line.mapNotNull { invisible[it] }.distinct().map { "$file:${index + 1}: $it" }
            }
        }

        assertEquals(emptyList(), findings)
    }
}
