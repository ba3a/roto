package com.roto.core.handoff

import java.io.IOException

/** A handoff Roto will not re-render, because doing so would change or lose the artist's work. */
class HandoffException(message: String, cause: Throwable? = null) : IOException(message, cause)
