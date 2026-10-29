package com.roto.core.cache

import com.roto.core.image.Raster
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CacheKeyTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-cache-key")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the same inputs give the same key`() {
        assertEquals(sampleKey(), sampleKey())
        assertEquals("mask", sampleKey().stage)
        assertTrue(Regex("[0-9a-f]{32}").matches(sampleKey().hash))
    }

    @Test
    fun `the encoding is pinned, so a key is the same on every machine and in every run`() {
        // Changing this value invalidates every cache on disk; do it only on purpose.
        assertEquals("0f2b5afe5af3aa84b3ce5e62425f5f21", sampleKey().hash)
    }

    @Test
    fun `stage, version and every kind of input reach the key`() {
        val base = sampleKey()

        assertNotEquals(base, sampleKey(stage = "plate"))
        assertNotEquals(base, sampleKey(version = 2))
        assertNotEquals(base, sampleKey(threshold = 0.300001))
        assertNotEquals(base, sampleKey(blur = 4))
        assertNotEquals(base, sampleKey(name = "walk_WEST"))
        assertNotEquals(base, sampleKey(holes = false))
        assertNotEquals(base, sampleKey(raster = ramp(4, 3).also { it[0, 0] = 1 }))
        assertNotEquals(base, sampleKey(parameters = MaskParameters(0.3, listOf(1, 3, 2))))
        assertNotEquals(base, sampleKey(upstream = CacheKey.of("plate", 1) { put("frames", 13) }))
    }

    @Test
    fun `values are framed, so neighbours cannot trade bytes`() {
        assertNotEquals(
            CacheKey.of("s", 1) { put("a", "bc") },
            CacheKey.of("s", 1) { put("ab", "c") },
        )
        assertNotEquals(
            CacheKey.of("s", 1) { put("a", "x"); put("b", "") },
            CacheKey.of("s", 1) { put("a", "x") },
        )
        assertNotEquals(CacheKey.of("s", 1) { put("n", 7) }, CacheKey.of("s", 1) { put("n", 7L) })
        assertNotEquals(CacheKey.of("s", 1) { put("r", ramp(2, 3)) }, CacheKey.of("s", 1) { put("r", ramp(3, 2)) })
    }

    @Test
    fun `doubles are keyed by bit pattern`() {
        assertEquals(CacheKey.of("s", 1) { put("d", Double.NaN) }, CacheKey.of("s", 1) { put("d", 0.0 / 0.0) })
        assertNotEquals(CacheKey.of("s", 1) { put("d", 0.0) }, CacheKey.of("s", 1) { put("d", -0.0) })
    }

    @Test
    fun `a file is keyed by its content, not its path`() {
        val first = tempRoot.resolve("IMG_0412.MOV").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val moved = tempRoot.resolve("renamed.mov").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val edited = tempRoot.resolve("edited.mov").also { it.writeBytes(byteArrayOf(1, 2, 4)) }

        val key = { file: Path -> CacheKey.of("frames", 1) { putFile("source", file) } }

        assertEquals(key(first), key(moved))
        assertNotEquals(key(first), key(edited))
    }

    @Test
    fun `a model reaches the key field by field, defaults included`() {
        val key = { parameters: MaskParameters ->
            CacheKey.of("s", 1) { put("p", parameters, MaskParameters.serializer()) }
        }

        assertEquals(key(MaskParameters(0.3, listOf(1, 2, 3), close = 2)), key(MaskParameters(0.3, listOf(1, 2, 3))))
        assertNotEquals(key(MaskParameters(0.3, listOf(1, 2, 3), close = 3)), key(MaskParameters(0.3, listOf(1, 2, 3))))
    }

    @Test
    fun `stage, version and input names are checked`() {
        assertFailsWith<IllegalArgumentException> { CacheKey.of("../mask", 1) {} }
        assertFailsWith<IllegalArgumentException> { CacheKey.of("", 1) {} }
        assertFailsWith<IllegalArgumentException> { CacheKey.of("mask", -1) {} }
        assertFailsWith<IllegalArgumentException> { CacheKey.of("mask", 1) { put("", 1) } }
        assertFailsWith<IllegalArgumentException> { CacheKey.of("mask", 1) { put("a", 1); put("a", 2) } }
        assertFailsWith<IllegalArgumentException> { CacheKey.of("mask", 1) { put("version", 2) } }
    }

    @Serializable
    private data class MaskParameters(val threshold: Double, val kernel: List<Int>, val close: Int = 2)

    @Suppress("LongParameterList")
    private fun sampleKey(
        stage: String = "mask",
        version: Int = 1,
        threshold: Double = 0.3,
        blur: Long = 3,
        name: String = "walk_EAST",
        holes: Boolean = true,
        raster: Raster = ramp(4, 3),
        parameters: MaskParameters = MaskParameters(0.3, listOf(1, 2, 3)),
        upstream: CacheKey = CacheKey.of("plate", 1) { put("frames", 12) },
    ): CacheKey = CacheKey.of(stage, version) {
        put("threshold", threshold)
        put("blur", blur)
        put("name", name)
        put("holes", holes)
        put("bytes", byteArrayOf(9, 8, 7))
        put("frame", raster)
        put("parameters", parameters, MaskParameters.serializer())
        put("plate", upstream)
    }

    private fun ramp(width: Int, height: Int): Raster =
        Raster.of(width, height) { x, y -> (0xFF shl 24) or (x * 40 shl 16) or (y * 60 shl 8) }
}
