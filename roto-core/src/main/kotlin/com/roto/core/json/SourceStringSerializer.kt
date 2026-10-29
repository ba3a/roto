package com.roto.core.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A value written in JSON as its source string, parsed (and so validated) on the way in. */
abstract class SourceStringSerializer<T : Any>(
    name: String,
    private val parse: (String) -> T,
    private val source: (T) -> String,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(source(value))

    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}
