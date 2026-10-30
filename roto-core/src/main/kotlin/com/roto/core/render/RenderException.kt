package com.roto.core.render

import java.io.IOException

/** A render Roto stopped: a profile or clip not set up far enough to draw, or a stage that cannot run on it. */
class RenderException(message: String, cause: Throwable? = null) : IOException(message, cause)
