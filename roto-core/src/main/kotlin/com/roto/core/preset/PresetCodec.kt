package com.roto.core.preset

import com.roto.core.json.JsonCodec

/** Reads and writes [PresetInfo] as `preset.json`; a bad file is a [PresetException]. */
object PresetCodec : JsonCodec<PresetInfo>(PresetInfo.serializer(), "preset.json", ::PresetException)
