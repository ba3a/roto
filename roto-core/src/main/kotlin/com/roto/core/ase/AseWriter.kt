package com.roto.core.ase

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import java.util.zip.Deflater

/**
 * Encodes an [AseSprite] as an indexed-colour `.ase` file.
 *
 * The first frame carries the palette and every layer chunk, as Aseprite itself writes them; each
 * frame then carries one compressed cel per layer that has one, in layer order. The palette is
 * written twice — the current palette chunk, then the old one — so readers that predate the
 * current chunk still get the colours.
 *
 * The output is a pure function of the sprite: no timestamps, no ordering taken from hash
 * collections, and the same zlib settings every time.
 */
internal object AseWriter {

    fun encode(sprite: AseSprite): ByteArray {
        val frames = sprite.frames.mapIndexed { index, frame ->
            encodeFrame(frame, if (index == 0) firstFrameChunks(sprite) else emptyList())
        }
        val fileSize = AseFormat.HEADER_SIZE + frames.sumOf { it.size }
        val out = LittleEndianOutput()
        writeHeader(out, sprite, fileSize)
        frames.forEach(out::bytes)
        return out.toByteArray()
    }

    private fun writeHeader(out: LittleEndianOutput, sprite: AseSprite, fileSize: Int) {
        out.dword(fileSize)
        out.word(AseFormat.HEADER_MAGIC)
        out.word(sprite.frames.size)
        out.word(sprite.width)
        out.word(sprite.height)
        out.word(AseFormat.COLOR_DEPTH_INDEXED)
        out.dword(AseFormat.FLAG_LAYER_OPACITY_VALID)
        // Deprecated global speed; readers that still use it get the first frame's duration.
        out.word(sprite.frames.first().durationMs)
        out.dword(0)
        out.dword(0)
        out.byte(sprite.transparentIndex)
        out.zeros(count = 3)
        out.word(paletteSizeField(sprite.palette.size))
        // Square pixels.
        out.byte(1)
        out.byte(1)
        // No grid.
        out.short(0)
        out.short(0)
        out.word(0)
        out.word(0)
        out.zeros(AseFormat.HEADER_RESERVED_TAIL)
        check(out.size == AseFormat.HEADER_SIZE) { "Header is ${out.size} bytes" }
    }

    private fun firstFrameChunks(sprite: AseSprite): List<ByteArray> = buildList {
        add(paletteChunk(sprite.palette))
        add(oldPaletteChunk(sprite.palette))
        sprite.layers.forEach { add(layerChunk(it)) }
    }

    private fun encodeFrame(frame: AseFrame, leadingChunks: List<ByteArray>): ByteArray {
        val chunks = leadingChunks + frame.cels.sortedBy { it.layerIndex }.map(::celChunk)
        val out = LittleEndianOutput()
        out.dword(AseFormat.FRAME_HEADER_SIZE + chunks.sumOf { it.size })
        out.word(AseFormat.FRAME_MAGIC)
        out.word(minOf(chunks.size, AseFormat.OLD_CHUNK_COUNT_OVERFLOW))
        out.word(frame.durationMs)
        out.zeros(count = 2)
        out.dword(chunks.size)
        chunks.forEach(out::bytes)
        return out.toByteArray()
    }

    private fun paletteChunk(palette: List<Int>): ByteArray = chunk(AseFormat.CHUNK_PALETTE) {
        dword(palette.size)
        dword(0)
        dword(palette.size - 1)
        zeros(AseFormat.PALETTE_RESERVED)
        for (color in palette) {
            word(AseFormat.PALETTE_ENTRY_FLAGS_NONE)
            byte(Argb.red(color))
            byte(Argb.green(color))
            byte(Argb.blue(color))
            byte(Argb.alpha(color))
        }
    }

    /** The pre-1.1 palette chunk: one packet, RGB only. */
    private fun oldPaletteChunk(palette: List<Int>): ByteArray = chunk(AseFormat.CHUNK_OLD_PALETTE) {
        word(1)
        byte(0)
        byte(paletteSizeField(palette.size))
        for (color in palette) {
            byte(Argb.red(color))
            byte(Argb.green(color))
            byte(Argb.blue(color))
        }
    }

    private fun layerChunk(layer: AseLayer): ByteArray = chunk(AseFormat.CHUNK_LAYER) {
        var flags = 0
        if (layer.visible) flags = flags or AseFormat.LAYER_FLAG_VISIBLE
        if (layer.editable) flags = flags or AseFormat.LAYER_FLAG_EDITABLE
        word(flags)
        word(AseFormat.LAYER_TYPE_NORMAL)
        // Child level: every layer sits at the top level.
        word(0)
        // Default width and height, ignored by readers.
        word(0)
        word(0)
        word(AseFormat.BLEND_MODE_NORMAL)
        byte(layer.opacity)
        zeros(AseFormat.LAYER_RESERVED)
        string(layer.name)
    }

    private fun celChunk(cel: AseCel): ByteArray = chunk(AseFormat.CHUNK_CEL) {
        word(cel.layerIndex)
        short(cel.x)
        short(cel.y)
        byte(cel.opacity)
        word(AseFormat.CEL_TYPE_COMPRESSED_IMAGE)
        // Z-index: the layer order decides stacking.
        short(0)
        zeros(AseFormat.CEL_RESERVED)
        word(cel.image.width)
        word(cel.image.height)
        bytes(deflate(cel.image))
    }

    private fun chunk(type: Int, body: LittleEndianOutput.() -> Unit): ByteArray {
        val data = LittleEndianOutput().apply(body).toByteArray()
        val out = LittleEndianOutput()
        out.dword(AseFormat.CHUNK_HEADER_SIZE + data.size)
        out.word(type)
        out.bytes(data)
        return out.toByteArray()
    }

    private fun paletteSizeField(size: Int): Int = if (size == AseFormat.FULL_PALETTE) 0 else size

    /** zlib-wrapped deflate of the row-major indices, one byte per pixel. */
    private fun deflate(image: IndexedRaster): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        try {
            deflater.setInput(image.toByteArray())
            deflater.finish()
            val out = LittleEndianOutput()
            val buffer = ByteArray(DEFLATE_BUFFER_SIZE)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                out.bytes(buffer.copyOf(count))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private const val DEFLATE_BUFFER_SIZE = 8192
}
