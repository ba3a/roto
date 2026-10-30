package com.roto.core.publish

import java.io.IOException

/** A clip manifest that is malformed or fails [ClipManifest]'s validation. */
class ClipManifestException(message: String, cause: Throwable? = null) : IOException(message, cause)
