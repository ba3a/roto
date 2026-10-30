package com.roto.core.settings

import java.io.IOException

/** A `settings.json` Roto cannot read: malformed, or holding a value that fails validation. */
class SettingsException(message: String, cause: Throwable? = null) : IOException(message, cause)
