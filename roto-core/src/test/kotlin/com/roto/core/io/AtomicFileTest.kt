package com.roto.core.io

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtomicFileTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-atomic")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `writes a new file, creating its folders`() {
        val file = tempRoot.resolve("a").resolve("b").resolve("out.txt")

        AtomicFile.write(file) { it.write("hello".toByteArray()) }

        assertEquals("hello", file.readText())
        assertEquals(listOf("out.txt"), file.parent.listDirectoryEntries().map { it.name })
    }

    @Test
    fun `replaces an existing file and leaves nothing else behind`() {
        val file = tempRoot.resolve("out.txt")
        file.writeText("old content that is longer")

        AtomicFile.write(file) { it.write("new".toByteArray()) }

        assertEquals("new", file.readText())
        assertEquals(listOf("out.txt"), tempRoot.listDirectoryEntries().map { it.name })
    }

    @Test
    fun `a failure halfway leaves the original untouched and no temporary file`() {
        val file = tempRoot.resolve("out.txt")
        file.writeText("original")

        assertFailsWith<IOException> {
            AtomicFile.write(file) {
                it.write("partial".toByteArray())
                throw IOException("disk full")
            }
        }

        assertEquals("original", file.readText())
        assertEquals(listOf("out.txt"), tempRoot.listDirectoryEntries().map { it.name })
    }
}
