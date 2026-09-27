/*
 * DynamicValue.kt
 * JsonUI
 *
 * A property value that may be a literal, a state binding, a script
 * expression, a template string or a localized string
 * (see docs/SCHEMA.md "Dynamic values").
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class DynamicValue {
    data class Literal(val literal: JsonElement) : DynamicValue()
    data class Binding(val path: JsonPath) : DynamicValue()
    data class Expression(val expression: String) : DynamicValue()
    data class Template(val parts: List<TemplatePart>) : DynamicValue()
    data class Localized(val key: String) : DynamicValue()

    sealed class TemplatePart {
        data class Text(val text: String) : TemplatePart()
        data class Expression(val expression: String) : TemplatePart()
    }

    val isLiteral: Boolean get() = this is Literal
    val bindingPath: JsonPath? get() = (this as? Binding)?.path

    /** The value written back to JSON. */
    val value: JsonElement
        get() = when (this) {
            is Literal -> {
                val s = (literal as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (s != null && (s.startsWith("$") || s.startsWith("@"))) JsonPrimitive(s[0] + s) else literal
            }
            is Binding -> JsonPrimitive("$" + path.toString())
            is Expression -> JsonPrimitive("\${$expression}")
            is Localized -> JsonPrimitive("@$key")
            is Template -> JsonPrimitive(parts.joinToString("") {
                when (it) {
                    is TemplatePart.Text -> it.text
                    is TemplatePart.Expression -> "\${${it.expression}}"
                }
            })
        }

    companion object {
        fun of(value: JsonElement): DynamicValue = when {
            value is JsonPrimitive && value.isString -> of(value.content)
            value is JsonObject && value.size == 1 && value["\$bind"]?.stringValue != null -> Binding(JsonPath.parse(value["\$bind"]!!.stringValue!!))
            value is JsonObject && value.size == 1 && value["\$expr"]?.stringValue != null -> Expression(value["\$expr"]!!.stringValue!!)
            else -> Literal(value)
        }

        fun of(s: String): DynamicValue {
            if (s.startsWith("$$") || s.startsWith("@@")) return Literal(JsonPrimitive(s.substring(1)))
            if (s.startsWith("@") && s.length > 1) return Localized(s.substring(1))
            if (s.startsWith("$") && s.length > 1 && !s.startsWith("\${")) return Binding(JsonPath.parse(s.substring(1)))
            if (s.contains("\${")) {
                val parts = templateParts(s)
                if (parts.size == 1 && parts[0] is TemplatePart.Expression) return Expression((parts[0] as TemplatePart.Expression).expression)
                if (parts.any { it is TemplatePart.Expression }) return Template(parts)
            }
            return Literal(JsonPrimitive(s))
        }

        /** Splits `"Hello ${state.name}!"` into text and expression parts, honouring nested braces. */
        fun templateParts(s: String): List<TemplatePart> {
            val parts = mutableListOf<TemplatePart>()
            val text = StringBuilder()
            var i = 0
            while (i < s.length) {
                if (s[i] == '$' && i + 1 < s.length && s[i + 1] == '{') {
                    var depth = 0
                    var j = i + 1
                    var closed = -1
                    while (j < s.length) {
                        if (s[j] == '{') depth++
                        else if (s[j] == '}') { depth--; if (depth == 0) { closed = j; break } }
                        j++
                    }
                    if (closed >= 0) {
                        if (text.isNotEmpty()) { parts.add(TemplatePart.Text(text.toString())); text.clear() }
                        parts.add(TemplatePart.Expression(s.substring(i + 2, closed).trim()))
                        i = closed + 1
                        continue
                    }
                }
                text.append(s[i])
                i++
            }
            if (text.isNotEmpty()) parts.add(TemplatePart.Text(text.toString()))
            return parts
        }
    }
}

// Convenience accessors used by both platforms' renderers.
val JsonElement.dynamic: DynamicValue get() = DynamicValue.of(this)

fun JsonElement.asArrayOrSingle(): List<JsonElement> = when (this) {
    is JsonArray -> toList()
    else -> if (isNull) emptyList() else listOf(this)
}
