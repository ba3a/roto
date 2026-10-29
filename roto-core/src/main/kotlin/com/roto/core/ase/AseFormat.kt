package com.roto.core.ase

/**
 * Field values of the Aseprite file format, as documented in Aseprite's `docs/ase-file-specs.md`.
 * All multi-byte fields are little-endian.
 */
internal object AseFormat {
    const val HEADER_SIZE = 128
    const val HEADER_MAGIC = 0xA5E0
    const val HEADER_RESERVED_TAIL = 84
    const val COLOR_DEPTH_INDEXED = 8

    /** Header flag: the opacity byte of each layer chunk is meaningful. */
    const val FLAG_LAYER_OPACITY_VALID = 1

    /** Header flag: every layer chunk ends with a 16-byte UUID. */
    const val FLAG_LAYER_UUID = 4
    const val UUID_SIZE = 16

    const val FRAME_HEADER_SIZE = 16
    const val FRAME_MAGIC = 0xF1FA

    /** Value of the frame header's old 16-bit chunk count when the real count needs the 32-bit field. */
    const val OLD_CHUNK_COUNT_OVERFLOW = 0xFFFF

    const val CHUNK_HEADER_SIZE = 6
    const val CHUNK_OLD_PALETTE = 0x0004

    /** The oldest palette chunk, with 6-bit channels `0..63`. */
    const val CHUNK_OLD_PALETTE_64 = 0x0011
    const val OLD_PALETTE_64_MAX = 63

    const val CHUNK_LAYER = 0x2004
    const val CHUNK_CEL = 0x2005
    const val CHUNK_PALETTE = 0x2019

    const val LAYER_FLAG_VISIBLE = 1
    const val LAYER_FLAG_EDITABLE = 2
    const val LAYER_TYPE_NORMAL = 0
    const val LAYER_TYPE_TILEMAP = 2
    const val BLEND_MODE_NORMAL = 0
    const val LAYER_RESERVED = 3

    const val CEL_TYPE_RAW_IMAGE = 0
    const val CEL_TYPE_LINKED = 1
    const val CEL_TYPE_COMPRESSED_IMAGE = 2
    const val CEL_RESERVED = 5

    const val PALETTE_RESERVED = 8
    const val PALETTE_ENTRY_FLAGS_NONE = 0
    const val PALETTE_ENTRY_HAS_NAME = 1

    /** In a palette size field, `0` stands for 256 entries. */
    const val FULL_PALETTE = 256
}
