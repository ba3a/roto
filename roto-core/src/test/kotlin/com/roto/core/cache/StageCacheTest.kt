package com.roto.core.cache

import com.roto.core.image.Raster
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StageCacheTest {

    private lateinit var cacheFolder: Path
    private lateinit var cache: StageCache

    private val key = CacheKey.of("plate", 1) { put("frames", 12) }
    private val otherKey = CacheKey.of("plate", 1) { put("frames", 13) }
    private val plate = Raster.of(3, 2) { x, y -> (0xFF shl 24) or (x * 50 shl 8) or y }

    @BeforeTest
    fun setUp() {
        cacheFolder = createTempDirectory("roto-cache").resolve("cache")
        cache = StageCache(cacheFolder)
    }

    @AfterTest
    fun tearDown() {
        cacheFolder.parent.toFile().deleteRecursively()
    }

    @Test
    fun `an output is computed once and then read back, also by a later run`() {
        var computed = 0
        val compute = { computed++; plate }

        assertEquals(plate, cache.get(key, RasterCacheCodec, compute))
        assertEquals(plate, cache.get(key, RasterCacheCodec, compute))
        assertEquals(plate, StageCache(cacheFolder).get(key, RasterCacheCodec, compute))

        assertEquals(1, computed)
        assertTrue(cache.contains(key))
    }

    @Test
    fun `each key has its own entry, under its stage`() {
        cache.get(key, RasterCacheCodec) { plate }
        cache.get(otherKey, RasterCacheCodec) { plate.copy().also { it[0, 0] = 0 } }

        val stageFolder = cacheFolder.toAbsolutePath().normalize().resolve("plate")
        val entries = stageFolder.listDirectoryEntries().map { it.name }.sorted()
        assertEquals(listOf(key.hash, otherKey.hash).sorted(), entries)
        assertEquals(0, cache.get(otherKey, RasterCacheCodec) { error("stored") }[0, 0])
    }

    @Test
    fun `a failed computation stores nothing`() {
        assertFailsWith<IllegalStateException> { cache.get(key, RasterCacheCodec) { error("ffmpeg died") } }

        assertFalse(cache.contains(key))
        assertNoTemporaryFolders()
    }

    @Test
    fun `a write that fails halfway leaves no entry and no temporary folder`() {
        val failing = object : CacheCodec<Raster> {
            override fun write(value: Raster, entry: Path) {
                entry.resolve("half.png").writeText("partial")
                throw IOException("disk full")
            }

            override fun read(entry: Path): Raster = error("never stored")
        }

        assertFailsWith<IOException> { cache.get(key, failing) { plate } }

        assertFalse(cache.contains(key))
        assertNoTemporaryFolders()
    }

    @Test
    fun `a damaged entry is discarded and computed again`() {
        cache.get(key, RasterCacheCodec) { plate }
        val stored = cacheFolder.resolve("plate").resolve(key.hash)
        stored.resolve("raster.png").writeText("not a png")

        var computed = 0
        assertEquals(plate, cache.get(key, RasterCacheCodec) { computed++; plate })

        assertEquals(1, computed)
        assertEquals(plate, cache.get(key, RasterCacheCodec) { error("repaired") })
    }

    @Test
    fun `a filled entry is filled once, then read from what fill wrote`() {
        var filled = 0
        val fill = { folder: Path ->
            filled++
            assertTrue(folder.listDirectoryEntries().isEmpty())
            folder.resolve("frame.txt").writeText("frame")
        }
        val read = { entry: Path -> entry to entry.resolve("frame.txt").readText() }

        val (entry, content) = cache.getOrFill(key, read, fill)
        assertEquals(entry to content, cache.getOrFill(key, read, fill))

        assertEquals(1, filled)
        assertEquals("frame", content)
        assertEquals(cacheFolder.toAbsolutePath().normalize().resolve("plate").resolve(key.hash), entry)
    }

    @Test
    fun `a filled entry that no longer reads back is filled again`() {
        val read = { entry: Path -> entry.resolve("frame.txt").readText() }
        cache.getOrFill(key, read) { it.resolve("frame.txt").writeText("frame") }
        Files.delete(cacheFolder.resolve("plate").resolve(key.hash).resolve("frame.txt"))

        assertEquals("again", cache.getOrFill(key, read) { it.resolve("frame.txt").writeText("again") })
    }

    @Test
    fun `when another writer stores the same key first, its entry is kept`() {
        val names = cache.getOrFill(key, { entry -> entry.listDirectoryEntries().map { it.name } }) { folder ->
            folder.resolve("mine.txt").writeText("mine")
            // Another process finishes the same key while this fill is still running.
            val theirs = folder.parent.resolve(key.hash)
            Files.createDirectories(theirs)
            theirs.resolve("theirs.txt").writeText("theirs")
        }

        assertEquals(listOf("theirs.txt"), names)
        assertNoTemporaryFolders()
    }

    @Test
    fun `clear removes every entry`() {
        cache.get(key, RasterCacheCodec) { plate }
        cache.getOrFill(CacheKey.of("frames", 1) {}, { it }) { it.resolve("000000.png").writeText("frame") }

        cache.clear()

        assertFalse(cache.contains(key))
        assertFalse(cacheFolder.exists())
        assertEquals(plate, cache.get(key, RasterCacheCodec) { plate })
    }

    private fun assertNoTemporaryFolders() {
        val stageFolder = cacheFolder.resolve("plate")
        if (stageFolder.exists()) {
            assertEquals(emptyList(), stageFolder.listDirectoryEntries().map { it.name }.filter { it.startsWith(".") })
        }
    }
}
