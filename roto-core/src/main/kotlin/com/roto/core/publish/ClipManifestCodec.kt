package com.roto.core.publish

import com.roto.core.json.JsonCodec

/** Reads and writes a published clip's manifest. */
object ClipManifestCodec : JsonCodec<ClipManifest>(ClipManifest.serializer(), "clip manifest", ::ClipManifestException)
