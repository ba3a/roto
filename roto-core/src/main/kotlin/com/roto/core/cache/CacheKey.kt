package com.roto.core.cache

import com.roto.core.template.SafeName
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Names one stage output: the [stage] that produced it and a [hash] of everything the output depends
 * on — the stage's algorithm version, its parameters and its inputs. The same inputs and parameters
 * always give the same key, on every machine and in every run; change any of them, or bump the
 * version when the algorithm changes, and the key changes, so a stale output is never read back.
 *
 * Inputs that are themselves stage outputs go in as their [CacheKey], not their content: a key
 * already stands for everything upstream of it, so the whole chain stays cheap to key.
 */
class CacheKey private constructor(val stage: String, val hash: String) {

    override fun equals(other: Any?): Boolean = other is CacheKey && stage == other.stage && hash == other.hash

    override fun hashCode(): Int = 31 * stage.hashCode() + hash.hashCode()

    override fun toString(): String = "$stage/$hash"

    companion object {
        /**
         * 128 bits of the SHA-256: far beyond any chance of collision among one profile's outputs, and
         * half the folder-name length, which matters under Windows' path limit.
         */
        private const val HASH_BYTES = 16

        /**
         * The key of [stage]'s output at algorithm [version] for what [inputs] puts in. [stage] names
         * the cache subfolder, so it follows [SafeName].
         */
        fun of(stage: String, version: Int, inputs: CacheKeyBuilder.() -> Unit): CacheKey {
            SafeName.validate(stage, "Stage")
            require(version >= 0) { "Stage version must not be negative, got $version" }
            val digest = MessageDigest.getInstance("SHA-256")
            CacheKeyBuilder(digest).apply {
                put("stage", stage)
                put("version", version)
                inputs()
            }
            val hash = HexFormat.of().formatHex(digest.digest(), 0, HASH_BYTES)
            return CacheKey(stage, hash)
        }
    }
}
