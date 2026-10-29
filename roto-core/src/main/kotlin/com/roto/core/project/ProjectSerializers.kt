package com.roto.core.project

import com.roto.core.json.SourceStringSerializer
import com.roto.core.template.PathTemplate
import com.roto.core.template.Template
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object TemplateSerializer :
    SourceStringSerializer<Template>("com.roto.core.template.Template", ::Template, Template::source)

internal object PathTemplateSerializer :
    SourceStringSerializer<PathTemplate>("com.roto.core.template.PathTemplate", ::PathTemplate, PathTemplate::source)

/** [MirrorPair] as the two-element array `roto.json` writes it in. */
internal object MirrorPairSerializer : KSerializer<MirrorPair> {

    private val list = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = list.descriptor

    override fun serialize(encoder: Encoder, value: MirrorPair) =
        encoder.encodeSerializableValue(list, listOf(value.first, value.second))

    override fun deserialize(decoder: Decoder): MirrorPair {
        val facings = decoder.decodeSerializableValue(list)
        if (facings.size != 2) throw SerializationException("A mirror pair must name exactly two facings: $facings")
        return MirrorPair(facings[0], facings[1])
    }
}
