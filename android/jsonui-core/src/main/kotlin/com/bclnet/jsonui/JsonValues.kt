/*
 * JsonValues.kt
 * JsonUI
 *
 * Helpers over kotlinx.serialization's JsonElement, the dynamically typed
 * JSON value shared by the whole library. Mirrors JsonValue.swift.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The lenient parser used everywhere in JsonUI. */
val JsonUIJson: Json = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }

fun parseJson(text: String): JsonElement = JsonUIJson.parseToJsonElement(text)

fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> jsonNumber(value.toDouble())
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonOf(it.value) })
    is Iterable<*> -> JsonArray(value.map { jsonOf(it) })
    is Array<*> -> JsonArray(value.map { jsonOf(it) })
    else -> JsonPrimitive(value.toString())
}

/** A number primitive in canonical form: integral values have no fraction (`8`, not `8.0`), matching parsed JSON. */
fun jsonNumber(n: Double): JsonPrimitive =
    if (n.isFinite() && n == Math.rint(n) && Math.abs(n) < 1e15) JsonPrimitive(n.toLong()) else JsonPrimitive(n)

/** Numeric-aware equality: `2` and `2.0` are equal, otherwise structural equality. */
fun jsonEquals(a: JsonElement, b: JsonElement): Boolean {
    if (a.isNumber && b.isNumber) return a.doubleValue == b.doubleValue
    return a == b
}

fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { it.first to jsonOf(it.second) })

fun jsonArrayOf(vararg items: Any?): JsonArray = JsonArray(items.map { jsonOf(it) })

val JsonElement.isNull: Boolean get() = this is JsonNull

val JsonElement.isBoolean: Boolean
    get() = this is JsonPrimitive && !isString && (content == "true" || content == "false")

val JsonElement.isNumber: Boolean
    get() = this is JsonPrimitive && !isString && !isBoolean && content.toDoubleOrNull() != null

val JsonElement.isString: Boolean get() = this is JsonPrimitive && isString

val JsonElement.boolValue: Boolean?
    get() = when {
        this is JsonNull -> false
        this is JsonPrimitive && isBoolean -> content == "true"
        this is JsonPrimitive && isNumber -> content.toDouble() != 0.0
        this is JsonPrimitive -> when (content.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0", "" -> false
            else -> null
        }
        else -> null
    }

val JsonElement.doubleValue: Double?
    get() = when {
        this is JsonPrimitive && isBoolean -> if (content == "true") 1.0 else 0.0
        this is JsonPrimitive && !isString -> content.toDoubleOrNull()
        this is JsonPrimitive -> content.trim().toDoubleOrNull()
        else -> null
    }

val JsonElement.intValue: Int? get() = doubleValue?.takeIf { it.isFinite() }?.toInt()

/** The string form used when a value is displayed as text. */
val JsonElement.stringValue: String?
    get() = when {
        this is JsonNull -> null
        this is JsonPrimitive && isString -> content
        this is JsonPrimitive && isNumber -> formatNumber(content.toDouble())
        this is JsonPrimitive -> content
        else -> toJsonString()
    }

val JsonElement.arrayValue: List<JsonElement>? get() = (this as? JsonArray)?.toList()

val JsonElement.objectValue: Map<String, JsonElement>? get() = this as? JsonObject

/** JavaScript truthiness. */
val JsonElement.isTruthy: Boolean
    get() = when {
        this is JsonNull -> false
        this is JsonPrimitive && isBoolean -> content == "true"
        this is JsonPrimitive && isNumber -> content.toDouble().let { it != 0.0 && !it.isNaN() }
        this is JsonPrimitive -> content.isNotEmpty()
        else -> true
    }

operator fun JsonElement.get(key: String): JsonElement = (this as? JsonObject)?.get(key) ?: JsonNull

operator fun JsonElement.get(index: Int): JsonElement = (this as? JsonArray)?.getOrNull(index) ?: JsonNull

fun formatNumber(n: Double): String =
    if (n.isFinite() && n == Math.rint(n) && Math.abs(n) < 1e15) n.toLong().toString() else n.toString()

// MARK: - Paths

/** Reads the value at a dotted / indexed path such as `phones[0].number`. */
fun JsonElement.valueAt(path: JsonPath): JsonElement {
    var current = this
    for (segment in path.segments) {
        current = when (segment) {
            is JsonPath.Segment.Key -> current[segment.key]
            is JsonPath.Segment.Index -> current[segment.index]
        }
        if (current.isNull) return JsonNull
    }
    return current
}

/** Returns a copy with `value` written at `path`, creating intermediate objects and arrays. */
fun JsonElement.withValue(value: JsonElement, path: JsonPath): JsonElement {
    val first = path.segments.firstOrNull() ?: return value
    val rest = JsonPath(path.segments.drop(1))
    return when (first) {
        is JsonPath.Segment.Key -> {
            val map = (this as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            val child = (map[first.key] ?: JsonNull).withValue(value, rest)
            if (child.isNull && rest.isEmpty) map.remove(first.key) else map[first.key] = child
            JsonObject(map)
        }
        is JsonPath.Segment.Index -> {
            val list = (this as? JsonArray)?.toMutableList() ?: mutableListOf()
            while (list.size <= first.index) list.add(JsonNull)
            list[first.index] = list[first.index].withValue(value, rest)
            JsonArray(list)
        }
    }
}

fun JsonElement.contains(path: JsonPath): Boolean {
    var current = this
    for (segment in path.segments) {
        current = when (segment) {
            is JsonPath.Segment.Key -> (current as? JsonObject)?.get(segment.key) ?: return false
            is JsonPath.Segment.Index -> (current as? JsonArray)?.getOrNull(segment.index) ?: return false
        }
    }
    return true
}

// MARK: - Text

/** Serializes to JSON text with sorted keys, so Swift and Kotlin output match. */
fun JsonElement.toJsonString(pretty: Boolean = false): String = buildString { write(this@toJsonString, this, pretty, 0) }

private fun write(value: JsonElement, out: StringBuilder, pretty: Boolean, indent: Int) {
    when (value) {
        is JsonNull -> out.append("null")
        is JsonPrimitive -> when {
            value.isString -> out.append(escapeJson(value.content))
            value.isBoolean -> out.append(value.content)
            else -> {
                val n = value.content.toDoubleOrNull()
                if (n == null || !n.isFinite()) out.append("null") else out.append(formatNumber(n))
            }
        }
        is JsonArray -> {
            if (value.isEmpty()) { out.append("[]"); return }
            out.append("[")
            value.forEachIndexed { i, v ->
                if (i > 0) out.append(",")
                if (pretty) out.append("\n").append("  ".repeat(indent + 1))
                write(v, out, pretty, indent + 1)
            }
            if (pretty) out.append("\n").append("  ".repeat(indent))
            out.append("]")
        }
        is JsonObject -> {
            if (value.isEmpty()) { out.append("{}"); return }
            out.append("{")
            value.keys.sorted().forEachIndexed { i, k ->
                if (i > 0) out.append(",")
                if (pretty) out.append("\n").append("  ".repeat(indent + 1))
                out.append(escapeJson(k)).append(if (pretty) ": " else ":")
                write(value.getValue(k), out, pretty, indent + 1)
            }
            if (pretty) out.append("\n").append("  ".repeat(indent))
            out.append("}")
        }
    }
}

fun escapeJson(s: String): String {
    val out = StringBuilder("\"")
    for (ch in s) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else -> if (ch.code < 0x20) out.append(String.format("\\u%04x", ch.code)) else out.append(ch)
        }
    }
    return out.append("\"").toString()
}
