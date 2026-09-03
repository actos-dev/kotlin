package dev.actos

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

internal object ContextualAnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Any =
        (decoder as? JsonDecoder)?.decodeJsonElement() ?: JsonElement.serializer().deserialize(decoder)

    override fun serialize(
        encoder: Encoder,
        value: Any,
    ) {
        if (value is JsonElement && encoder is JsonEncoder) {
            encoder.encodeJsonElement(value)
        } else {
            encoder.encodeString(value.toString())
        }
    }
}

public val ActosJson: Json =
    Json {
        ignoreUnknownKeys = true // §16 Forward compatibility
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        serializersModule =
            SerializersModule {
                contextual(Any::class, ContextualAnySerializer)
            }
    }
