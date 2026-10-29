package com.roto.core.palette

import java.io.IOException

/** A palette file that is malformed, of an unknown kind, or not a valid [Palette]. */
class PaletteFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)
