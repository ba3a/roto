package com.roto.core.palette

import com.roto.core.image.Argb
import com.roto.core.json.SourceStringSerializer

/** Opaque colours written as `#rrggbb`, the way palette files and `ramps.json` write them. */
object HexColor {

    private const val DIGITS = 6
    private const val RADIX = 16
    private const val OPAQUE = 0xFF shl 24
    private const val RGB_MASK = 0xFFFFFF

    /** The opaque [Argb] colour [text] names: six hex digits, after a `#` unless [hashOptional]. */
    fun parse(text: String, hashOptional: Boolean = false): Int {
        require(hashOptional || text.startsWith("#")) { error(text) }
        val digits = text.removePrefix("#")
        require(digits.length == DIGITS && digits.all { it.isHexDigit() }) { error(text) }
        return OPAQUE or digits.toInt(RADIX)
    }

    /** [argb] as `#rrggbb`, lower case; its alpha is not written. */
    fun format(argb: Int): String = "#" + (argb and RGB_MASK).toString(RADIX).padStart(DIGITS, '0')

    fun isOpaque(argb: Int): Boolean = Argb.alpha(argb) == Argb.OPAQUE_ALPHA

    private fun error(text: String) = "Colour '$text' is not #rrggbb"

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/** An opaque colour stored in JSON as `#rrggbb`. */
object HexColorSerializer : SourceStringSerializer<Int>("HexColor", { HexColor.parse(it) }, HexColor::format)
