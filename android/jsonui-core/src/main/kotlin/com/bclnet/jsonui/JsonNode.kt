/*
 * JsonNode.kt
 * JsonUI
 *
 * A node of the form definition: `{ "type": "TextField", "title": "Email", "text": "$email" }`.
 */
package com.bclnet.jsonui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable(with = JsonNode.Serializer::class)
data class JsonNode(val type: String, val props: Map<String, JsonElement> = emptyMap()) {

    /** Well known node types. Custom types are looked up in the host's view registry. */
    enum class Kind(val typeName: String) {
        Text("Text"), Label("Label"), Image("Image"), Link("Link"), ProgressView("ProgressView"),
        TextField("TextField"), SecureField("SecureField"), TextEditor("TextEditor"),
        Toggle("Toggle"), Picker("Picker"), DatePicker("DatePicker"), Slider("Slider"), Stepper("Stepper"), Button("Button"),
        Form("Form"), Section("Section"), List("List"), VStack("VStack"), HStack("HStack"), ZStack("ZStack"),
        ScrollView("ScrollView"), Group("Group"), NavigationView("NavigationView"), Spacer("Spacer"), Divider("Divider"),
        ForEach("ForEach"), If("If");

        companion object {
            private val byName = entries.associateBy { it.typeName }
            fun of(type: String): Kind? = byName[type]
        }
    }

    constructor(kind: Kind, props: Map<String, JsonElement> = emptyMap()) : this(kind.typeName, props)

    val kind: Kind? get() = Kind.of(type)

    val id: String? get() = props["id"]?.stringValue

    operator fun get(key: String): JsonElement = props[key] ?: JsonNull

    fun has(key: String): Boolean = props.containsKey(key)

    /** The child nodes of `content` (a single node or an array of nodes). */
    val content: List<JsonNode> get() = nodes("content")

    /** The nodes stored under `key`, accepting a single node or an array. */
    fun nodes(key: String): List<JsonNode> = nodesOf(this[key])

    /** A single node stored under `key`, if the value is a node object. */
    fun node(key: String): JsonNode? = fromValue(this[key])

    /** The object value of this node. */
    val value: JsonObject get() = JsonObject(props + ("type" to JsonPrimitive(type)))

    fun with(key: String, value: JsonElement?): JsonNode =
        if (value == null || value.isNull) copy(props = props - key) else copy(props = props + (key to value))

    fun with(key: String, value: Any?): JsonNode = with(key, jsonOf(value))

    fun withContent(nodes: List<JsonNode>): JsonNode =
        with("content", if (nodes.size == 1) nodes[0].value else JsonArray(nodes.map { it.value }))

    override fun toString(): String = value.toJsonString(pretty = true)

    companion object {
        /** Strips a leading colon (`":Text"` → `"Text"`), any module prefix and a generic suffix. */
        fun normalize(type: String): String {
            var t = type
            if (t.startsWith(":")) t = t.substring(1)
            val dot = t.lastIndexOf('.')
            if (dot >= 0 && !t.startsWith("$")) t = t.substring(dot + 1)
            val generic = t.indexOf('<')
            if (generic >= 0) t = t.substring(0, generic)
            return t
        }

        /** Builds a node from an object value that has a `type` key. */
        fun fromValue(value: JsonElement): JsonNode? {
            val obj = value as? JsonObject ?: return null
            val type = obj["type"]?.stringValue ?: return null
            return JsonNode(normalize(type), obj - "type")
        }

        fun nodesOf(value: JsonElement): List<JsonNode> = when (value) {
            is JsonArray -> value.mapNotNull { fromValue(it) }
            is JsonObject -> listOfNotNull(fromValue(value))
            else -> emptyList()
        }

        operator fun invoke(type: String, props: Map<String, JsonElement> = emptyMap()): JsonNode = JsonNode(normalize(type), props)
    }

    object Serializer : KSerializer<JsonNode> {
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
        override fun deserialize(decoder: Decoder): JsonNode {
            val obj = decoder.decodeSerializableValue(JsonObject.serializer())
            return fromValue(obj) ?: throw IllegalArgumentException("JsonUI: expected an object with a \"type\" key")
        }
        override fun serialize(encoder: Encoder, value: JsonNode) {
            encoder.encodeSerializableValue(JsonObject.serializer(), value.value)
        }
    }
}
