package com.roto.core.palette

import com.roto.core.json.JsonCodec

/** Reads and writes a project's `ramps.json` as a [RampSet]. */
object RampSetCodec : JsonCodec<RampSet>(RampSet.serializer(), "ramps.json", ::RampSetException)
