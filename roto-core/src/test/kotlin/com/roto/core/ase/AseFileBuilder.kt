package com.roto.core.ase

import java.util.zip.Deflater

/**
 * Hand-assembles `.ase` files chunk by chunk, so reader tests can feed it what Aseprite and
 * LibreSprite produce on save — linked cels, raw cels, extra chunks, old palettes — and malformed
 * files, none of which [AseWriter] ever writes. Field layout follows the format spec directly.
 */
internal class AseFileBuilder(
    private val width: Int = 2,
    private val height: Int = 2,
    private val colorDepth: Int = 8,
    private val flags: Int = 1,
    private val transparentIndex: Int = 0,
) {
    private val frames = mutableListOf<ByteArray>()

    fun frame(vararg chunks: ByteArray, durationMs: Int = 100, newChunkCount: Boolean = true): AseFileBuilder {
        val out = LittleEndianOutput()
        out.dword(16 + chunks.sumOf { it.size })
        out.word(0xF1FA)
        out.word(chunks.size)
        out.word(durationMs)
        out.zeros(2)
        out.dword(if (newChunkCount) chunks.size else 0)
        chunks.forEach(out::bytes)
        frames += out.toByteArray()
        return this
    }

    fun build(): ByteArray {
        val out = LittleEndianOutput()
        out.dword(128 + frames.sumOf { it.size })
        out.word(0xA5E0)
        out.word(frames.size)
        out.word(width)
        out.word(height)
        out.word(colorDepth)
        out.dword(flags)
        out.word(100)
        out.dword(0)
        out.dword(0)
        out.byte(transparentIndex)
        out.zeros(3)
        out.word(0)
        out.byte(1)
        out.byte(1)
        out.zeros(92)
        frames.forEach(out::bytes)
        return out.toByteArray()
    }

    companion object {
        fun chunk(type: Int, body: LittleEndianOutput.() -> Unit): ByteArray {
            val data = LittleEndianOutput().apply(body).toByteArray()
            return LittleEndianOutput().apply {
                dword(6 + data.size)
                word(type)
                bytes(data)
            }.toByteArray()
        }

        /** A 0x2019 palette chunk; [names] gives entries that carry the optional name string. */
        fun palette(colours: List<Int>, names: Map<Int, String> = emptyMap()): ByteArray = chunk(0x2019) {
            dword(colours.size)
            dword(0)
            dword(colours.size - 1)
            zeros(8)
            colours.forEachIndexed { index, argb ->
                val name = names[index]
                word(if (name != null) 1 else 0)
                byte(argb ushr 16 and 0xFF)
                byte(argb ushr 8 and 0xFF)
                byte(argb and 0xFF)
                byte(argb ushr 24 and 0xFF)
                if (name != null) string(name)
            }
        }

        /** A 0x0004 (8-bit) or 0x0011 (6-bit) palette chunk with one packet of RGB triples. */
        fun legacyPalette(type: Int, rgb: List<Triple<Int, Int, Int>>): ByteArray = chunk(type) {
            word(1)
            byte(0)
            byte(rgb.size)
            rgb.forEach { (r, g, b) ->
                byte(r)
                byte(g)
                byte(b)
            }
        }

        fun layer(
            name: String,
            flags: Int = 3,
            type: Int = 0,
            childLevel: Int = 0,
            opacity: Int = 255,
            uuid: Boolean = false,
        ): ByteArray = chunk(0x2004) {
            word(flags)
            word(type)
            word(childLevel)
            word(0)
            word(0)
            word(0)
            byte(opacity)
            zeros(3)
            string(name)
            if (type == 2) dword(0)
            if (uuid) zeros(16)
        }

        private fun LittleEndianOutput.celHeader(layer: Int, x: Int, y: Int, opacity: Int, type: Int) {
            word(layer)
            short(x)
            short(y)
            byte(opacity)
            word(type)
            short(0)
            zeros(5)
        }

        fun compressedCel(layer: Int, width: Int, height: Int, indices: ByteArray, x: Int = 0, y: Int = 0) =
            chunk(0x2005) {
                celHeader(layer, x, y, 255, type = 2)
                word(width)
                word(height)
                bytes(deflate(indices))
            }

        fun rawCel(layer: Int, width: Int, height: Int, indices: ByteArray, opacity: Int = 255) = chunk(0x2005) {
            celHeader(layer, 0, 0, opacity, type = 0)
            word(width)
            word(height)
            bytes(indices)
        }

        fun linkedCel(layer: Int, toFrame: Int) = chunk(0x2005) {
            celHeader(layer, 0, 0, 255, type = 1)
            word(toFrame)
        }

        fun tilemapCel(layer: Int) = chunk(0x2005) {
            celHeader(layer, 0, 0, 255, type = 3)
            zeros(32)
        }

        private fun deflate(bytes: ByteArray): ByteArray {
            val deflater = Deflater()
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(bytes.size + 64)
            val size = deflater.deflate(buffer)
            deflater.end()
            return buffer.copyOf(size)
        }
    }
}
