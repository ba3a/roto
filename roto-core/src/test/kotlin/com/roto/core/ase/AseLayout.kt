package com.roto.core.ase

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * A deliberately naive walk over an `.ase` file's byte layout, written straight from the format
 * spec and independent of the production code, so writer tests check offsets rather than trusting
 * the writer's own idea of them.
 */
internal class AseLayout(bytes: ByteArray) {
    val header: ByteBuffer = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).slice().order(ByteOrder.LITTLE_ENDIAN)
    val frames: List<Frame>

    init {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(HEADER_SIZE)
        frames = List(header.getShort(6).toInt() and 0xFFFF) { readFrame(buffer) }
        check(!buffer.hasRemaining()) { "${buffer.remaining()} bytes after the last frame" }
    }

    data class Frame(val durationMs: Int, val oldChunkCount: Int, val chunks: List<Chunk>)

    class Chunk(val type: Int, val data: ByteBuffer)

    private fun readFrame(buffer: ByteBuffer): Frame {
        val start = buffer.position()
        val size = buffer.int
        check(buffer.short.toInt() and 0xFFFF == FRAME_MAGIC) { "Bad frame magic at $start" }
        val oldChunkCount = buffer.short.toInt() and 0xFFFF
        val duration = buffer.short.toInt() and 0xFFFF
        buffer.position(buffer.position() + 2)
        val chunkCount = buffer.int
        val chunks = List(chunkCount) { readChunk(buffer) }
        check(buffer.position() - start == size) { "Frame at $start declares $size bytes" }
        return Frame(duration, oldChunkCount, chunks)
    }

    private fun readChunk(buffer: ByteBuffer): Chunk {
        val size = buffer.int
        val type = buffer.short.toInt() and 0xFFFF
        val dataSize = size - CHUNK_HEADER_SIZE
        val data = buffer.slice(buffer.position(), dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(buffer.position() + dataSize)
        return Chunk(type, data)
    }

    companion object {
        const val HEADER_SIZE = 128
        const val FRAME_MAGIC = 0xF1FA
        const val CHUNK_HEADER_SIZE = 6

        fun ByteBuffer.u8(): Int = get().toInt() and 0xFF

        fun ByteBuffer.u16(): Int = short.toInt() and 0xFFFF

        fun ByteBuffer.s16(): Int = short.toInt()

        fun ByteBuffer.skip(count: Int): ByteBuffer = position(position() + count)

        fun ByteBuffer.string(): String {
            val bytes = ByteArray(u16())
            get(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        fun ByteBuffer.inflateRest(expectedSize: Int): ByteArray {
            val compressed = ByteArray(remaining())
            get(compressed)
            val inflater = Inflater()
            try {
                inflater.setInput(compressed)
                val out = ByteArray(expectedSize)
                val count = inflater.inflate(out)
                check(count == expectedSize && inflater.finished()) { "Inflated $count of $expectedSize bytes" }
                return out
            } finally {
                inflater.end()
            }
        }
    }
}
