/*
 * JsonDocument.kt
 * JsonUI
 *
 * The top level document: an optional `_ui` header (initial state, script,
 * strings) followed by the root node.
 */
package com.bclnet.jsonui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class JsonUIHeader(
    val version: Int = CURRENT_VERSION,
    val state: Map<String, JsonElement> = emptyMap(),
    val script: String? = null,
    val strings: Map<String, String> = emptyMap(),
) {
    val value: JsonObject
        get() {
            val map = linkedMapOf<String, JsonElement>("version" to JsonPrimitive(version))
            if (state.isNotEmpty()) map["state"] = JsonObject(state)
            if (script != null) map["script"] = JsonPrimitive(script)
            if (strings.isNotEmpty()) map["strings"] = JsonObject(strings.mapValues { JsonPrimitive(it.value) })
            return JsonObject(map)
        }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromValue(value: JsonElement): JsonUIHeader = JsonUIHeader(
            version = value["version"].intValue ?: CURRENT_VERSION,
            state = value["state"].objectValue ?: emptyMap(),
            script = value["script"].stringValue,
            strings = (value["strings"].objectValue ?: emptyMap()).mapNotNull { (k, v) -> v.stringValue?.let { k to it } }.toMap(),
        )
    }
}

class JsonDocumentException(message: String) : IllegalArgumentException(message)

@Serializable(with = JsonDocument.Serializer::class)
data class JsonDocument(val header: JsonUIHeader = JsonUIHeader(), val root: JsonNode) {

    val value: JsonObject
        get() = JsonObject(root.props + ("type" to JsonPrimitive(root.type)) + (HEADER_KEY to header.value))

    fun toJsonString(pretty: Boolean = true): String = value.toJsonString(pretty)

    companion object {
        const val HEADER_KEY = "_ui"

        fun fromValue(value: JsonElement): JsonDocument {
            val obj = value as? JsonObject ?: throw JsonDocumentException("JsonUI: the document must be a JSON object")
            val header = obj[HEADER_KEY]?.let { JsonUIHeader.fromValue(it) } ?: JsonUIHeader()
            if (header.version > JsonUIHeader.CURRENT_VERSION) throw JsonDocumentException("JsonUI: unsupported document version ${header.version}")
            val root = JsonNode.fromValue(JsonObject(obj - HEADER_KEY)) ?: throw JsonDocumentException("JsonUI: the root node has no \"type\"")
            return JsonDocument(header, root)
        }

        fun parse(json: String): JsonDocument = fromValue(parseJson(json))
    }

    object Serializer : KSerializer<JsonDocument> {
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
        override fun deserialize(decoder: Decoder): JsonDocument = fromValue(decoder.decodeSerializableValue(JsonObject.serializer()))
        override fun serialize(encoder: Encoder, value: JsonDocument) = encoder.encodeSerializableValue(JsonObject.serializer(), value.value)
    }
}
