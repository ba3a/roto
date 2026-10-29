package com.roto.core.project

import java.io.IOException

/** A `roto.json` that is malformed or fails [ProjectConfig]'s validation. */
class ProjectConfigException(message: String, cause: Throwable? = null) : IOException(message, cause)
