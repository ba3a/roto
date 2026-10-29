package com.roto.core.palette

import java.io.IOException

/** A `ramps.json` that is malformed, fails [RampSet]'s validation, or uses colours its palette lacks. */
class RampSetException(message: String, cause: Throwable? = null) : IOException(message, cause)
