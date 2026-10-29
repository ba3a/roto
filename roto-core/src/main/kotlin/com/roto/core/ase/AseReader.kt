package com.roto.core.ase

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Decodes an indexed-colour `.ase` file into an [AseSprite].
 *
 * Built to read back what [AseWriter] wrote after an artist has opened and saved it in Aseprite or
 * LibreSprite, so it accepts what those editors add on save — linked and uncompressed cels, colour
 * profile, tag, user-data and other chunks that carry nothing [AseSprite] models (skipped), palette
 * entry names, layer UUIDs, either palette chunk generation. What it cannot represent faithfully it
 * rejects with an [AseFormatException] naming the feature: non-indexed colour, group and tilemap
 * layers, tilemap cels, a palette that changes between frames.
 */
internal object AseReader {

    fun decode(bytes: ByteArray): AseSprite {
        val input = LittleEndianInput(bytes)
        val header = readHeader(input, bytes.size)
        val document = Document(header)
        try {
            for (frameIndex in 0 until header.frameCount) {
                readFrame(input, frameIndex, document)
            }
            return document.toSprite()
        } catch (e: IllegalArgumentException) {
            throw AseFormatException("Invalid sprite: ${e.message}", e)
        }
    }

    private class Header(
        val frameCount: Int,
        val width: Int,
        val height: Int,
        val flags: Int,
        val transparentIndex: Int,
    )

    /** Everything read so far, assembled into an [AseSprite] once the last frame is in. */
    private class Document(val header: Header) {
        val layers = mutableListOf<AseLayer>()
        val frames = mutableListOf<AseFrame>()
        var palette: IntArray? = null
        var legacyPalette: IntArray? = null

        fun toSprite(): AseSprite {
            val colours = palette ?: legacyPalette ?: throw AseFormatException("File has no palette")
            return AseSprite(
                width = header.width,
                height = header.height,
                palette = colours.toList(),
                layers = layers.toList(),
                frames = frames.toList(),
                transparentIndex = header.transparentIndex,
            )
        }
    }

    private fun readHeader(input: LittleEndianInput, actualSize: Int): Header {
        val header = input.sub(AseFormat.HEADER_SIZE)
        val fileSize = header.dword()
        checkFormat(header.word() == AseFormat.HEADER_MAGIC) { "Not an .ase file: bad header magic" }
        checkFormat(fileSize <= actualSize) { "File is truncated: $actualSize of $fileSize bytes" }
        val frameCount = header.word()
        val width = header.word()
        val height = header.word()
        val colorDepth = header.word()
        checkFormat(colorDepth == AseFormat.COLOR_DEPTH_INDEXED) {
            "Only indexed-colour sprites are supported; this one is $colorDepth bits per pixel"
        }
        val flags = header.dword()
        // Deprecated speed, then two reserved DWORDs.
        header.skip(count = 10)
        val transparentIndex = header.byte()
        return Header(frameCount, width, height, flags, transparentIndex)
    }

    private fun readFrame(input: LittleEndianInput, frameIndex: Int, document: Document) {
        val frameSize = input.dword()
        checkFormat(input.word() == AseFormat.FRAME_MAGIC) { "Frame $frameIndex: bad frame magic" }
        val oldChunkCount = input.word()
        val durationMs = input.word()
        input.skip(count = 2)
        val newChunkCount = input.dword()
        val chunkCount = if (newChunkCount == 0) oldChunkCount else newChunkCount
        val body = input.sub(frameSize - AseFormat.FRAME_HEADER_SIZE)
        val cels = mutableListOf<AseCel>()
        repeat(chunkCount) {
            val chunkSize = body.dword()
            val type = body.word()
            val data = body.sub(chunkSize - AseFormat.CHUNK_HEADER_SIZE)
            readChunk(type, data, frameIndex, document, cels)
        }
        document.frames += AseFrame(durationMs, cels)
    }

    private fun readChunk(
        type: Int,
        data: LittleEndianInput,
        frameIndex: Int,
        document: Document,
        cels: MutableList<AseCel>,
    ) {
        when (type) {
            AseFormat.CHUNK_PALETTE -> {
                requireFirstFrame(frameIndex, "palette")
                document.palette = readPalette(data)
            }
            AseFormat.CHUNK_OLD_PALETTE, AseFormat.CHUNK_OLD_PALETTE_64 -> {
                requireFirstFrame(frameIndex, "palette")
                document.legacyPalette = readLegacyPalette(data, sixBit = type == AseFormat.CHUNK_OLD_PALETTE_64)
            }
            AseFormat.CHUNK_LAYER -> document.layers += readLayer(data, document.header.flags)
            AseFormat.CHUNK_CEL -> cels += readCel(data, frameIndex, document.frames)
            // Colour profile, tags, user data, cel extras, slices and the rest describe nothing AseSprite holds.
            else -> Unit
        }
    }

    private fun requireFirstFrame(frameIndex: Int, what: String) {
        checkFormat(frameIndex == 0) { "Frame $frameIndex changes the $what; per-frame palettes are not supported" }
    }

    private fun readPalette(data: LittleEndianInput): IntArray {
        val size = data.dword()
        val first = data.dword()
        val last = data.dword()
        checkFormat(size in 1..AseFormat.FULL_PALETTE && first <= last && last < size) {
            "Palette chunk covers $first..$last of $size entries"
        }
        data.skip(AseFormat.PALETTE_RESERVED)
        val colours = IntArray(size) { Argb.TRANSPARENT }
        for (index in first..last) {
            val flags = data.word()
            colours[index] = Argb.of(red = data.byte(), green = data.byte(), blue = data.byte(), alpha = data.byte())
            if (flags and AseFormat.PALETTE_ENTRY_HAS_NAME != 0) data.string()
        }
        return colours
    }

    /** The pre-1.1 palette chunks: packets of opaque RGB entries, each after a skip. */
    private fun readLegacyPalette(data: LittleEndianInput, sixBit: Boolean): IntArray {
        val colours = IntArray(AseFormat.FULL_PALETTE) { Argb.TRANSPARENT }
        var next = 0
        var size = 0
        repeat(data.word()) {
            next += data.byte()
            val count = data.byte().let { if (it == 0) AseFormat.FULL_PALETTE else it }
            checkFormat(next + count <= AseFormat.FULL_PALETTE) { "Old palette chunk overruns 256 entries" }
            repeat(count) {
                colours[next++] = Argb.of(
                    red = legacyChannel(data.byte(), sixBit),
                    green = legacyChannel(data.byte(), sixBit),
                    blue = legacyChannel(data.byte(), sixBit),
                )
            }
            size = maxOf(size, next)
        }
        checkFormat(size > 0) { "Old palette chunk is empty" }
        return colours.copyOf(size)
    }

    private fun legacyChannel(value: Int, sixBit: Boolean): Int =
        if (sixBit) {
            (value.coerceAtMost(AseFormat.OLD_PALETTE_64_MAX) * Argb.OPAQUE_ALPHA + AseFormat.OLD_PALETTE_64_MAX / 2) /
                AseFormat.OLD_PALETTE_64_MAX
        } else {
            value
        }

    private fun readLayer(data: LittleEndianInput, headerFlags: Int): AseLayer {
        val flags = data.word()
        val type = data.word()
        val childLevel = data.word()
        // Default width and height, then blend mode: nothing AseLayer holds.
        data.skip(count = 6)
        val opacity = data.byte()
        data.skip(AseFormat.LAYER_RESERVED)
        val name = data.string()
        checkFormat(type == AseFormat.LAYER_TYPE_NORMAL && childLevel == 0) {
            "Layer '$name': group and tilemap layers are not supported"
        }
        if (headerFlags and AseFormat.FLAG_LAYER_UUID != 0) data.skip(AseFormat.UUID_SIZE)
        return AseLayer(
            name = name,
            opacity = if (headerFlags and AseFormat.FLAG_LAYER_OPACITY_VALID != 0) opacity else Argb.OPAQUE_ALPHA,
            visible = flags and AseFormat.LAYER_FLAG_VISIBLE != 0,
            editable = flags and AseFormat.LAYER_FLAG_EDITABLE != 0,
        )
    }

    private fun readCel(data: LittleEndianInput, frameIndex: Int, earlierFrames: List<AseFrame>): AseCel {
        val layerIndex = data.word()
        val x = data.short()
        val y = data.short()
        val opacity = data.byte()
        val type = data.word()
        // Z-index (stacking is taken from layer order) and reserved bytes.
        data.skip(count = 2 + AseFormat.CEL_RESERVED)
        return when (type) {
            AseFormat.CEL_TYPE_RAW_IMAGE, AseFormat.CEL_TYPE_COMPRESSED_IMAGE -> {
                val width = data.word()
                val height = data.word()
                val indices = if (type == AseFormat.CEL_TYPE_RAW_IMAGE) {
                    data.bytes(width * height)
                } else {
                    inflate(data.bytes(data.remaining), width * height)
                }
                AseCel(layerIndex, IndexedRaster.fromByteArray(width, height, indices), x, y, opacity)
            }
            AseFormat.CEL_TYPE_LINKED -> linkedCel(layerIndex, data.word(), frameIndex, earlierFrames)
            else -> throw AseFormatException("Frame $frameIndex layer $layerIndex: tilemap cels are not supported")
        }
    }

    /** A linked cel shares image, position and opacity with the cel it points at, so it reads as a copy. */
    private fun linkedCel(
        layerIndex: Int,
        linkedFrame: Int,
        frameIndex: Int,
        earlierFrames: List<AseFrame>,
    ): AseCel {
        checkFormat(linkedFrame < frameIndex) {
            "Frame $frameIndex layer $layerIndex links forward to frame $linkedFrame"
        }
        val linked = earlierFrames[linkedFrame].cels.firstOrNull { it.layerIndex == layerIndex }
        checkFormat(linked != null) {
            "Frame $frameIndex layer $layerIndex links to an empty cel in frame $linkedFrame"
        }
        return linked!!
    }

    private fun inflate(compressed: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(compressed)
            val out = ByteArray(expectedSize)
            var filled = 0
            while (filled < expectedSize && !inflater.finished()) {
                val count = inflater.inflate(out, filled, expectedSize - filled)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                filled += count
            }
            checkFormat(filled == expectedSize) { "Cel data inflates to $filled of $expectedSize bytes" }
            return out
        } catch (e: DataFormatException) {
            throw AseFormatException("Cel data is not valid zlib: ${e.message}", e)
        } finally {
            inflater.end()
        }
    }
}
