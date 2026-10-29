package com.roto.core.profile

import java.io.IOException

/** A `profile.json` that is malformed or fails [Profile]'s validation. */
class ProfileException(message: String, cause: Throwable? = null) : IOException(message, cause)
