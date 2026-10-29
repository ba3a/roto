package com.roto.core.project

import com.roto.core.json.JsonCodec

/**
 * Reads and writes [ProjectConfig] as `roto.json`, which sits at the consuming project's root; a bad
 * file is a [ProjectConfigException].
 */
object ProjectConfigCodec : JsonCodec<ProjectConfig>(ProjectConfig.serializer(), "roto.json", ::ProjectConfigException)
