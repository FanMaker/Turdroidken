package com.fanmaker.sdk

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Double -> encoder.encodeDouble(value)
            is Boolean -> encoder.encodeBoolean(value)
            is Map<*, *> -> encoder.encodeSerializableValue(
                MapSerializer(String.serializer(), this),
                value as Map<String, Any>
            )
            is List<*> -> encoder.encodeSerializableValue(
                ListSerializer(this),
                value as List<Any>
            )
            else -> throw SerializationException("Unsupported type: ${value::class}")
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This class can only be deserialized from JSON")

        val jsonElement = jsonDecoder.decodeJsonElement()

        return when (jsonElement) {
            is JsonPrimitive -> {
                when {
                    jsonElement.isString -> jsonElement.content
                    jsonElement.content == "true" || jsonElement.content == "false" -> jsonElement.content.toBoolean()
                    jsonElement.content.toIntOrNull() != null -> jsonElement.content.toInt()
                    jsonElement.content.toDoubleOrNull() != null -> jsonElement.content.toDouble()
                    else -> throw SerializationException("Unsupported primitive type")
                }
            }
            is JsonObject -> jsonDecoder.json.decodeFromJsonElement(
                MapSerializer(String.serializer(), AnySerializer), jsonElement
            )
            is JsonArray -> jsonDecoder.json.decodeFromJsonElement(
                ListSerializer(AnySerializer), jsonElement
            )
            else -> throw SerializationException("Unsupported JSON element type")
        }
    }
}