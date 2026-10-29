package com.roto.core.cache

import com.roto.core.image.Raster
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CacheCodecTest {

    private lateinit var entry: Path

    @BeforeTest
    fun setUp() {
        entry = createTempDirectory("roto-cache-entry")
    }

    @AfterTest
    fun tearDown() {
        entry.toFile().deleteRecursively()
    }

    @Test
    fun `a raster round-trips`() {
        val raster = frame(7)

        RasterCacheCodec.write(raster, entry)

        assertEquals(raster, RasterCacheCodec.read(entry))
    }

    @Test
    fun `a raster sequence round-trips in order, past ten frames`() {
        val frames = (0 until 12).map { frame(it) }

        RasterSequenceCacheCodec.write(frames, entry)

        assertEquals(frames, RasterSequenceCacheCodec.read(entry))
        assertEquals("000011.png", entry.listDirectoryEntries().map { it.name }.max())
        assertEquals(emptyList(), RasterSequenceCacheCodec.read(createEmpty("empty")))
    }

    @Test
    fun `a model round-trips and a damaged one is a cache error`() {
        val codec = JsonCacheCodec(Track.serializer())
        val track = Track(listOf(10.5, 11.0, 11.25), period = 12)

        codec.write(track, entry)

        assertEquals(track, codec.read(entry))
        entry.resolve("value.json").writeText("{ \"rootX\": [1.0] ")
        assertFailsWith<CacheException> { codec.read(entry) }
    }

    @Serializable
    private data class Track(val rootX: List<Double>, val period: Int)

    private fun createEmpty(name: String): Path = entry.resolve(name).also { it.toFile().mkdirs() }

    private fun frame(seed: Int): Raster =
        Raster.of(4, 3) { x, y -> (0xFF shl 24) or (seed * 20 shl 16) or (x shl 8) or y }
}
