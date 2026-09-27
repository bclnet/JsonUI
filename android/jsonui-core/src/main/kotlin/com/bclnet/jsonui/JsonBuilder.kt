/*
 * JsonBuilder.kt
 * JsonUI
 *
 * A small builder DSL for assembling documents in Kotlin, the counterpart of
 * JsonBuilder.swift.
 *
 *     val doc = jsonDocument(state = mapOf("email" to "")) {
 *         form {
 *             section("Account") {
 *                 textField("Email", text = "\$email").keyboard("email")
 *                 button("Sign in", host = "login")
 *             }
 *         }
 *     }
 *     doc.toJsonString()
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@DslMarker
annotation class JsonUIDsl

@JsonUIDsl
class JsonNodeBuilder {
    private val nodes = mutableListOf<JsonNode>()

    fun build(): List<JsonNode> = nodes.toList()

    /** Adds a node and returns it so modifiers can be chained; the chained result replaces it. */
    operator fun JsonNode.unaryPlus(): JsonNode { nodes.add(this); return this }

    private fun add(node: JsonNode): JsonNode { nodes.add(node); return node }

    private fun replaceLast(old: JsonNode, new: JsonNode) {
        val idx = nodes.lastIndexOf(old)
        if (idx >= 0) nodes[idx] = new else nodes.add(new)
    }

    /** Applies modifiers to the most recently added node: `text("x") { padding(8) }` style is supported via [modify]. */
    fun JsonNode.modify(block: JsonNode.() -> JsonNode): JsonNode {
        val new = block(this)
        replaceLast(this, new)
        return new
    }

    private fun container(kind: JsonNode.Kind, props: Map<String, JsonElement> = emptyMap(), content: JsonNodeBuilder.() -> Unit): JsonNode =
        add(JsonNode(kind, props).withContent(JsonNodeBuilder().apply(content).build()))

    private fun props(vararg pairs: Pair<String, Any?>): Map<String, JsonElement> =
        pairs.filter { it.second != null }.associate { it.first to jsonOf(it.second) }

    // Views
    fun text(text: Any) = add(JsonNode(JsonNode.Kind.Text, props("text" to text)))
    fun label(title: Any, systemImage: String) = add(JsonNode(JsonNode.Kind.Label, props("title" to title, "systemImage" to systemImage)))
    fun image(systemName: String? = null, name: String? = null, url: Any? = null) =
        add(JsonNode(JsonNode.Kind.Image, props("systemName" to systemName, "name" to name, "url" to url)))
    fun link(title: Any, url: Any) = add(JsonNode(JsonNode.Kind.Link, props("title" to title, "url" to url)))
    fun progressView(value: Any? = null, label: Any? = null) = add(JsonNode(JsonNode.Kind.ProgressView, props("value" to value, "label" to label)))
    fun textField(title: Any, text: String) = add(JsonNode(JsonNode.Kind.TextField, props("title" to title, "text" to text)))
    fun secureField(title: Any, text: String) = add(JsonNode(JsonNode.Kind.SecureField, props("title" to title, "text" to text)))
    fun textEditor(text: String) = add(JsonNode(JsonNode.Kind.TextEditor, props("text" to text)))
    fun toggle(label: Any, isOn: String) = add(JsonNode(JsonNode.Kind.Toggle, props("label" to label, "isOn" to isOn)))
    fun picker(label: Any, selection: String, options: Any) = add(JsonNode(JsonNode.Kind.Picker, props("label" to label, "selection" to selection, "options" to options)))
    fun datePicker(label: Any, selection: String, components: String = "date") =
        add(JsonNode(JsonNode.Kind.DatePicker, props("label" to label, "selection" to selection, "components" to components)))
    fun slider(value: String, min: Double = 0.0, max: Double = 1.0, step: Double? = null, label: Any? = null) =
        add(JsonNode(JsonNode.Kind.Slider, props("value" to value, "min" to min, "max" to max, "step" to step, "label" to label)))
    fun stepper(label: Any, value: String, min: Double? = null, max: Double? = null, step: Double? = null) =
        add(JsonNode(JsonNode.Kind.Stepper, props("label" to label, "value" to value, "min" to min, "max" to max, "step" to step)))
    fun button(label: Any, action: JsonAction) = add(JsonNode(JsonNode.Kind.Button, props("label" to label, "action" to action.value)))
    fun button(label: Any, script: String) = button(label, JsonAction.Script(script))
    fun button(label: Any, host: String, args: Map<String, Any?> = emptyMap()) =
        button(label, JsonAction.Host(host, args.mapValues { jsonOf(it.value) }))
    fun spacer(minLength: Double? = null) = add(JsonNode(JsonNode.Kind.Spacer, props("minLength" to minLength)))
    fun divider() = add(JsonNode(JsonNode.Kind.Divider))

    // Containers
    fun form(content: JsonNodeBuilder.() -> Unit) = container(JsonNode.Kind.Form, content = content)
    fun section(header: Any? = null, footer: Any? = null, content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.Section, props("header" to header, "footer" to footer), content)
    fun list(content: JsonNodeBuilder.() -> Unit) = container(JsonNode.Kind.List, content = content)
    fun vstack(alignment: String? = null, spacing: Double? = null, content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.VStack, props("alignment" to alignment, "spacing" to spacing), content)
    fun hstack(alignment: String? = null, spacing: Double? = null, content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.HStack, props("alignment" to alignment, "spacing" to spacing), content)
    fun zstack(alignment: String? = null, content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.ZStack, props("alignment" to alignment), content)
    fun scrollView(axis: String = "vertical", content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.ScrollView, props("axis" to axis), content)
    fun group(content: JsonNodeBuilder.() -> Unit) = container(JsonNode.Kind.Group, content = content)
    fun navigationView(title: Any? = null, content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.NavigationView, props("title" to title), content)
    fun forEach(data: Any, item: String = "item", index: String = "index", content: JsonNodeBuilder.() -> Unit) =
        container(JsonNode.Kind.ForEach, props("data" to data, "item" to item, "index" to index), content)
    fun ifThen(condition: Any, content: JsonNodeBuilder.() -> Unit, elseContent: (JsonNodeBuilder.() -> Unit)? = null): JsonNode {
        var node = JsonNode(JsonNode.Kind.If, props("condition" to condition)).withContent(JsonNodeBuilder().apply(content).build())
        val elseNodes = elseContent?.let { JsonNodeBuilder().apply(it).build() } ?: emptyList()
        if (elseNodes.isNotEmpty()) node = node.with("else", if (elseNodes.size == 1) elseNodes[0].value else JsonArray(elseNodes.map { it.value }))
        return add(node)
    }
    fun custom(type: String, props: Map<String, Any?> = emptyMap(), content: (JsonNodeBuilder.() -> Unit)? = null): JsonNode {
        val node = JsonNode(type, props.mapValues { jsonOf(it.value) })
        val nodes = content?.let { JsonNodeBuilder().apply(it).build() } ?: emptyList()
        return add(if (nodes.isEmpty()) node else node.withContent(nodes))
    }

    // Modifiers, chainable on the value returned by the view functions.
    fun JsonNode.id(id: String) = modify { with("id", id) }
    fun JsonNode.padding() = modify { with("padding", true) }
    fun JsonNode.padding(length: Double) = modify { with("padding", length) }
    fun JsonNode.padding(edges: String, length: Double? = null) = modify { with("padding", jsonObjectOf("edges" to edges, "length" to length).compact()) }
    fun JsonNode.padding(top: Double = 0.0, leading: Double = 0.0, bottom: Double = 0.0, trailing: Double = 0.0) =
        modify { with("padding", jsonObjectOf("top" to top, "leading" to leading, "bottom" to bottom, "trailing" to trailing)) }
    fun JsonNode.frame(width: Double? = null, height: Double? = null, minWidth: Double? = null, maxWidth: Any? = null, minHeight: Double? = null, maxHeight: Any? = null, alignment: String? = null) =
        modify { with("frame", jsonObjectOf("width" to width, "height" to height, "minWidth" to minWidth, "maxWidth" to maxWidth, "minHeight" to minHeight, "maxHeight" to maxHeight, "alignment" to alignment).compact()) }
    fun JsonNode.font(font: Any) = modify { with("font", font) }
    fun JsonNode.bold(value: Any = true) = modify { with("bold", value) }
    fun JsonNode.italic(value: Any = true) = modify { with("italic", value) }
    fun JsonNode.foregroundColor(color: Any) = modify { with("foregroundColor", color) }
    fun JsonNode.background(color: Any) = modify { with("background", color) }
    fun JsonNode.cornerRadius(radius: Double) = modify { with("cornerRadius", radius) }
    fun JsonNode.border(color: Any, width: Double = 1.0) = modify { with("border", jsonObjectOf("color" to color, "width" to width)) }
    fun JsonNode.opacity(value: Any) = modify { with("opacity", value) }
    fun JsonNode.hidden(value: Any = true) = modify { with("hidden", value) }
    fun JsonNode.disabled(value: Any = true) = modify { with("disabled", value) }
    fun JsonNode.onAppear(action: JsonAction) = modify { with("onAppear", action.value) }
    fun JsonNode.onTap(action: JsonAction) = modify { with("onTap", action.value) }
    fun JsonNode.onChange(action: JsonAction) = modify { with("onChange", action.value) }
    fun JsonNode.onCommit(action: JsonAction) = modify { with("onCommit", action.value) }
    fun JsonNode.accessibilityLabel(label: String) = modify { with("accessibilityLabel", label) }
    fun JsonNode.keyboard(keyboard: String) = modify { with("keyboard", keyboard) }
    fun JsonNode.autocapitalization(value: String) = modify { with("autocapitalization", value) }
    fun JsonNode.error(message: Any) = modify { with("error", message) }
    fun JsonNode.style(style: Any) = modify { with("style", style) }
    fun JsonNode.role(role: String) = modify { with("role", role) }
    fun JsonNode.lineLimit(limit: Int) = modify { with("lineLimit", limit) }
    fun JsonNode.multilineTextAlignment(alignment: String) = modify { with("multilineTextAlignment", alignment) }
    /** Appends an ordered modifier (`{"type": "padding", "length": 8}`). */
    fun JsonNode.modifier(type: String, props: Map<String, Any?> = emptyMap()) = modify {
        val modifiers = (this["modifiers"].arrayValue ?: emptyList()) + JsonObject(props.mapValues { jsonOf(it.value) } + ("type" to JsonPrimitive(type)))
        with("modifiers", JsonArray(modifiers))
    }

    private fun JsonObject.compact(): JsonObject = JsonObject(filterValues { !it.isNull })
}

/** Builds a document. A single root node is used as is; several are wrapped in a `Group`. */
fun jsonDocument(header: JsonUIHeader = JsonUIHeader(), content: JsonNodeBuilder.() -> Unit): JsonDocument {
    val nodes = JsonNodeBuilder().apply(content).build()
    val root = if (nodes.size == 1) nodes[0] else JsonNode(JsonNode.Kind.Group).withContent(nodes)
    return JsonDocument(header, root)
}

fun jsonDocument(state: Map<String, Any?> = emptyMap(), script: String? = null, strings: Map<String, String> = emptyMap(), content: JsonNodeBuilder.() -> Unit): JsonDocument =
    jsonDocument(JsonUIHeader(state = state.mapValues { jsonOf(it.value) }, script = script, strings = strings), content)

/** Builds a standalone node list, for custom renderers that need child nodes. */
fun jsonNodes(content: JsonNodeBuilder.() -> Unit): List<JsonNode> = JsonNodeBuilder().apply(content).build()
