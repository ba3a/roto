package com.roto.core.model

import java.io.IOException

/** A model's `model.json` or frames could not be read, written or understood; the message says why. */
class ModelException(message: String, cause: Throwable? = null) : IOException(message, cause)
