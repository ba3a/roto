package com.roto.core.json

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/**
 * The one kotlinx `Json` configuration in Roto. Every JSON file goes through it by way of [JsonCodec],
 * and a cache key hashes a parameter model through it, so a model always has one text form: fields in
 * declaration order, defaults written out, pretty-printed with `\n` line endings.
 */
internal object CanonicalJson {

    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun <T> encode(serializer: SerializationStrategy<T>, value: T): String = json.encodeToString(serializer, value)
}
