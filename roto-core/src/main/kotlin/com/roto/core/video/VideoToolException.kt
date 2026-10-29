package com.roto.core.video

import java.io.IOException

/** `ffmpeg` or `ffprobe` could not be started, failed, or produced output Roto cannot use. */
class VideoToolException(message: String, cause: Throwable? = null) : IOException(message, cause)
