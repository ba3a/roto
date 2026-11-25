package com.roto.core.model

import com.roto.core.json.JsonCodec

/** Reads and writes a model's `model.json` as a [ModelManifest]. */
object ModelCodec : JsonCodec<ModelManifest>(ModelManifest.serializer(), "model.json", ::ModelException)
