package com.roto.core.image

/**
 * Packing and unpacking of a pixel colour stored as one `Int` in `0xAARRGGBB` order, 8 bits per
 * channel, sRGB, straight (non-premultiplied) alpha. This is the layout of
 * [java.awt.image.BufferedImage.TYPE_INT_ARGB], so a [Raster] moves to and from AWT without
 * conversion.
 */
object Argb {
    const val TRANSPARENT: Int = 0
    const val OPAQUE_ALPHA: Int = 0xFF

    private const val CHANNEL_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Packs four channels, each `0..255`, into one pixel. */
    fun of(red: Int, green: Int, blue: Int, alpha: Int = OPAQUE_ALPHA): Int {
        require(red in 0..CHANNEL_MASK) { "red out of range: $red" }
        require(green in 0..CHANNEL_MASK) { "green out of range: $green" }
        require(blue in 0..CHANNEL_MASK) { "blue out of range: $blue" }
        require(alpha in 0..CHANNEL_MASK) { "alpha out of range: $alpha" }
        return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    fun alpha(argb: Int): Int = (argb ushr ALPHA_SHIFT) and CHANNEL_MASK

    fun red(argb: Int): Int = (argb ushr RED_SHIFT) and CHANNEL_MASK

    fun green(argb: Int): Int = (argb ushr GREEN_SHIFT) and CHANNEL_MASK

    fun blue(argb: Int): Int = argb and CHANNEL_MASK

    /** `#AARRGGBB`, for test failures and log lines. */
    fun format(argb: Int): String = "#%08X".format(argb)
}
