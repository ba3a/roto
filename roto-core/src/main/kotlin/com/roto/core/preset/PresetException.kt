package com.roto.core.preset

import java.io.IOException

/** A preset, or a preset bundle, that is malformed or fails validation. */
class PresetException(message: String, cause: Throwable? = null) : IOException(message, cause)
