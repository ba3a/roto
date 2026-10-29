package com.roto.core.cache

import java.io.IOException

/** A cache entry whose content is not readable as what its stage wrote. */
class CacheException(message: String, cause: Throwable? = null) : IOException(message, cause)
