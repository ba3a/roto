package com.roto.core.publish

import java.io.IOException

/** A publish Roto stopped: a clip not ready, a mirror it may not make, or an afterPublish command that failed. */
class PublishException(message: String, cause: Throwable? = null) : IOException(message, cause)
