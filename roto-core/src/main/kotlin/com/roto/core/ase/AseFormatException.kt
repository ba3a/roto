package com.roto.core.ase

import java.io.IOException

/** An `.ase` file that is malformed, or that uses a feature outside the subset [AseSprite] models. */
class AseFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Like `require`, but a failed [condition] is a bad file: throws [AseFormatException]. */
internal inline fun checkFormat(condition: Boolean, message: () -> String) {
    if (!condition) throw AseFormatException(message())
}
