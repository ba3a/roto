package com.roto.core.cache

import com.roto.core.image.Raster
import com.roto.core.json.CanonicalJson
import kotlinx.serialization.SerializationStrategy
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Feeds the inputs and parameters of one stage output into its [CacheKey].
 *
 * Every value goes in under a name, unique within the key, and is encoded unambiguously: a type tag,
 * then fixed-width numbers or length-prefixed bytes. So `"ab", "c"` and `"a", "bc"` hash differently,
 * as do an `Int` and a `Long` of the same value, or a 2×3 and a 3×2 raster of the same pixels. The
 * names `stage` and `version` are taken by [CacheKey.of] itself.
 */
class CacheKeyBuilder internal constructor(private val digest: MessageDigest) {

    private val names = HashSet<String>()

    fun put(name: String, value: String) = entry(name, STRING) { bytes(value.toByteArray(Charsets.UTF_8)) }

    fun put(name: String, value: Int) = entry(name, INT) { int(value) }

    fun put(name: String, value: Long) = entry(name, LONG) { long(value) }

    /** By bit pattern, so every NaN is one value and `0.0` and `-0.0` are two. */
    fun put(name: String, value: Double) = entry(name, DOUBLE) { long(value.toBits()) }

    fun put(name: String, value: Boolean) = entry(name, BOOLEAN) { digest.update(if (value) 1 else 0) }

    fun put(name: String, value: ByteArray) = entry(name, BYTES) { bytes(value) }

    fun put(name: String, value: Raster) = entry(name, RASTER) {
        int(value.width)
        int(value.height)
        val pixels = value.toArgbArray()
        val buffer = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
        buffer.asIntBuffer().put(pixels)
        digest.update(buffer.array())
    }

    /** An upstream stage's output, by its key: the key already covers everything that output came from. */
    fun put(name: String, value: CacheKey) = entry(name, KEY) {
        bytes(value.stage.toByteArray(Charsets.UTF_8))
        bytes(value.hash.toByteArray(Charsets.UTF_8))
    }

    /**
     * A parameter model, by its canonical JSON. Every field reaches the key, defaults included, so a
     * field added to the model later can never be left out of it. The model must not hold a collection
     * whose iteration order is unspecified, such as a `HashMap`.
     */
    fun <T> put(name: String, value: T, serializer: SerializationStrategy<T>) = entry(name, MODEL) {
        bytes(CanonicalJson.encode(serializer, value).toByteArray(Charsets.UTF_8))
    }

    /** A file, by its content: moving or renaming it keeps the key, editing it changes the key. */
    fun putFile(name: String, file: Path) = entry(name, FILE) {
        val content = MessageDigest.getInstance(digest.algorithm)
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(FILE_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                content.update(buffer, 0, read)
            }
        }
        bytes(content.digest())
    }

    private inline fun entry(name: String, tag: Byte, value: () -> Unit) {
        require(name.isNotEmpty()) { "A cache key input needs a name" }
        require(names.add(name)) { "Cache key input '$name' is put twice" }
        bytes(name.toByteArray(Charsets.UTF_8))
        digest.update(tag)
        value()
    }

    private fun int(value: Int) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }

    private fun long(value: Long) {
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    }

    private fun bytes(value: ByteArray) {
        int(value.size)
        digest.update(value)
    }

    private companion object {
        const val FILE_BUFFER_BYTES = 1 shl 16

        const val STRING: Byte = 1
        const val INT: Byte = 2
        const val LONG: Byte = 3
        const val DOUBLE: Byte = 4
        const val BOOLEAN: Byte = 5
        const val BYTES: Byte = 6
        const val RASTER: Byte = 7
        const val KEY: Byte = 8
        const val MODEL: Byte = 9
        const val FILE: Byte = 10
    }
}
