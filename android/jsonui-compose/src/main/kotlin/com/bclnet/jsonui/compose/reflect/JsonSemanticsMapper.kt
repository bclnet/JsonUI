/*
 * JsonSemanticsMapper.kt
 * JsonUI
 *
 * Maps a tree of ReflectedElements (a Compose semantics snapshot) to a
 * JsonUI document. Compose has no value tree to mirror the way SwiftUI does,
 * so the semantics tree is the closest thing to "the existing view": it
 * carries text, inputs, toggles, buttons, sliders and geometry. Layout
 * containers are inferred from child geometry (stacked vertically → VStack,
 * horizontally → HStack, overlapping → ZStack).
 *
 * Original `onClick`, `SetText` and `SetProgress` actions are registered as
 * host actions so the rendered copy still drives the original composable.
 */
package com.bclnet.jsonui.compose.reflect

import com.bclnet.jsonui.JsonAction
import com.bclnet.jsonui.JsonActionHandler
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.JsonUIHeader
import com.bclnet.jsonui.get
import com.bclnet.jsonui.jsonOf
import com.bclnet.jsonui.jsonNumber
import com.bclnet.jsonui.stringValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class JsonReflection(val document: JsonDocument, val actions: JsonActions, val warnings: List<String>)

class JsonSemanticsMapper(val actions: JsonActions = JsonActions()) {
    private val state = linkedMapOf<String, JsonElement>()
    private val usedKeys = mutableSetOf<String>()
    private val warnings = mutableListOf<String>()
    private val keyCounters = mutableMapOf<String, Int>()
    private var actionCounter = 0

    fun map(root: ReflectedElement): JsonReflection {
        val nodes = nodes(root.children.ifEmpty { listOf(root) })
        val rootNode = if (nodes.size == 1) nodes[0] else layoutNode(root.children.ifEmpty { listOf(root) }, nodes)
        return JsonReflection(JsonDocument(JsonUIHeader(state = state.toMap()), rootNode), actions, warnings.toList())
    }

    // MARK: - Elements

    private fun nodes(elements: List<ReflectedElement>): List<JsonNode> {
        val result = mutableListOf<JsonNode>()
        var i = 0
        while (i < elements.size) {
            val element = elements[i]
            if (element.role == "RadioButton") {
                // Consecutive radio buttons form one picker.
                var j = i
                while (j < elements.size && elements[j].role == "RadioButton") j++
                result.add(radioPicker(elements.subList(i, j)))
                i = j
                continue
            }
            result.addAll(node(element))
            i++
        }
        return result
    }

    private fun node(element: ReflectedElement): List<JsonNode> {
        val node: JsonNode? = when {
            element.editableText != null && element.setText != null -> textField(element)
            element.toggleState != null -> toggle(element)
            element.progress != null && element.setProgress != null -> slider(element)
            element.progress != null -> JsonNode(JsonNode.Kind.ProgressView, props("value" to element.progress.current.toDouble(), "total" to element.progress.max.toDouble(), "label" to element.label.ifEmpty { null }))
            element.role == "Button" || (element.onClick != null && element.children.none { it.onClick != null || it.editableText != null }) -> button(element)
            element.role == "Image" -> JsonNode(JsonNode.Kind.Image, props("systemName" to (element.contentDescription ?: "photo")))
            element.role == "DropdownList" -> textOrNull(element)
            element.children.isEmpty() -> textOrNull(element)
            else -> null
        }
        if (node != null) return listOf(withCommon(node, element))
        // A container: text of its own plus laid-out children.
        val own = element.text.map { JsonNode(JsonNode.Kind.Text, props("text" to it)) }
        val children = nodes(element.children)
        if (own.isEmpty() && children.size == 1) return listOf(withCommon(children[0], element))
        val laidOut = layoutNode(element.children, own + children)
        return listOf(withCommon(laidOut, element))
    }

    private fun textOrNull(element: ReflectedElement): JsonNode? {
        val text = element.label
        if (text.isEmpty() && element.contentDescription == null) return null
        var node = JsonNode(JsonNode.Kind.Text, props("text" to text))
        if (element.heading) node = node.with("font", "headline")
        return node
    }

    private fun textField(element: ReflectedElement): JsonNode {
        val key = key(element, "text", JsonPrimitive(element.editableText ?: ""))
        val title = element.text.firstOrNull { it != element.editableText } ?: element.contentDescription
        val kind = if (element.isPassword) JsonNode.Kind.SecureField else JsonNode.Kind.TextField
        var node = JsonNode(kind, props("title" to title, "text" to "\$$key"))
        element.setText?.let { setText ->
            val name = registerAction(element, "setText") { args -> setText(args.stringValue ?: "") }
            node = node.with("onChange", JsonAction.Host(name, mapOf("value" to JsonPrimitive("\$$key"))).value)
        }
        return node
    }

    private fun toggle(element: ReflectedElement): JsonNode {
        val key = key(element, "isOn", JsonPrimitive(element.toggleState == true))
        var node = JsonNode(JsonNode.Kind.Toggle, props("label" to element.label, "isOn" to "\$$key"))
        element.onClick?.let { click -> node = node.with("onChange", JsonAction.Host(registerAction(element, "toggle") { click() }).value) }
        return node
    }

    private fun slider(element: ReflectedElement): JsonNode {
        val progress = element.progress!!
        val key = key(element, "value", jsonNumber(progress.current.toDouble()))
        var node = JsonNode(JsonNode.Kind.Slider, props("value" to "\$$key", "min" to progress.min.toDouble(), "max" to progress.max.toDouble(), "label" to element.label.ifEmpty { null }))
        if (progress.steps > 0) node = node.with("step", (progress.max - progress.min).toDouble() / (progress.steps + 1))
        element.setProgress?.let { set ->
            val name = registerAction(element, "setProgress") { args -> set((args.stringValue?.toDoubleOrNull() ?: 0.0).toFloat()) }
            node = node.with("onChange", JsonAction.Host(name, mapOf("value" to JsonPrimitive("\$$key"))).value)
        }
        return node
    }

    private fun button(element: ReflectedElement): JsonNode {
        var node = JsonNode(JsonNode.Kind.Button, props("label" to element.label))
        element.onClick?.let { click -> node = node.with("action", JsonAction.Host(registerAction(element, "action") { click() }).value) }
            ?: warnings.add("button \"${element.label}\" has no click action")
        return node
    }

    private fun radioPicker(radios: List<ReflectedElement>): JsonNode {
        val labels = radios.map { it.label }
        val selected = radios.firstOrNull { it.selected == true }?.label ?: labels.firstOrNull() ?: ""
        val key = key(radios.first(), "selection", JsonPrimitive(selected))
        var node = JsonNode(JsonNode.Kind.Picker, props("selection" to "\$$key", "style" to "inline", "options" to labels))
        val clicks = radios.associate { it.label to it.onClick }
        if (clicks.values.any { it != null }) {
            val name = registerAction(radios.first(), "select") { args -> clicks[args.stringValue]?.invoke() }
            node = node.with("onChange", JsonAction.Host(name, mapOf("value" to JsonPrimitive("\$$key"))).value)
        }
        return node
    }

    // MARK: - Layout inference

    fun layoutNode(elements: List<ReflectedElement>, nodes: List<JsonNode>): JsonNode {
        val kind = when (inferLayout(elements)) {
            Layout.Vertical -> JsonNode.Kind.VStack
            Layout.Horizontal -> JsonNode.Kind.HStack
            Layout.Overlay -> JsonNode.Kind.ZStack
        }
        var node = JsonNode(kind).withContent(nodes)
        if (kind == JsonNode.Kind.VStack && elements.isNotEmpty() && elements.all { it.bounds.left == elements[0].bounds.left } && elements.any { it.bounds.width != elements[0].bounds.width }) {
            node = node.with("alignment", "leading")
        }
        return node
    }

    enum class Layout { Vertical, Horizontal, Overlay }

    fun inferLayout(elements: List<ReflectedElement>): Layout {
        if (elements.size < 2) return Layout.Vertical
        val sorted = elements.map { it.bounds }
        val vertical = sorted.zipWithNext().all { (a, b) -> b.top >= a.bottom - 0.5f }
        if (vertical) return Layout.Vertical
        val horizontal = sorted.zipWithNext().all { (a, b) -> b.left >= a.right - 0.5f }
        if (horizontal) return Layout.Horizontal
        // Mixed: mostly vertical wins, otherwise treat as an overlay.
        val verticalPairs = sorted.zipWithNext().count { (a, b) -> b.top >= a.bottom - 0.5f }
        val horizontalPairs = sorted.zipWithNext().count { (a, b) -> b.left >= a.right - 0.5f }
        return when {
            verticalPairs >= horizontalPairs && verticalPairs > 0 -> Layout.Vertical
            horizontalPairs > 0 -> Layout.Horizontal
            else -> Layout.Overlay
        }
    }

    // MARK: - Helpers

    private fun withCommon(node: JsonNode, element: ReflectedElement): JsonNode {
        var result = node
        if (element.disabled && node.kind != JsonNode.Kind.Text) result = result.with("disabled", true)
        if (element.onClick != null && node.kind != JsonNode.Kind.Button && node.kind != JsonNode.Kind.Toggle && node.kind != JsonNode.Kind.Picker) {
            result = result.with("onTap", JsonAction.Host(registerAction(element, "tap") { element.onClick.invoke() }).value)
        }
        element.contentDescription?.takeIf { node.kind != JsonNode.Kind.Image && node.kind != JsonNode.Kind.Text }?.let { result = result.with("accessibilityLabel", it) }
        return result
    }

    private fun key(element: ReflectedElement, prefix: String, initial: JsonElement): String {
        val base = element.key ?: element.testTag ?: "$prefix${keyCounters.merge(prefix, 1, Int::plus)}"
        var candidate = base
        var n = 1
        while (candidate in usedKeys) candidate = "$base${++n}"
        usedKeys.add(candidate)
        state[candidate] = initial
        return candidate
    }

    private fun registerAction(element: ReflectedElement, prefix: String, handler: (JsonElement) -> Unit): String {
        val name = element.actionName?.takeUnless { it in actions } ?: "$prefix${++actionCounter}"
        actions.register(name, JsonActionHandler { _, args, _ -> handler(args["value"].takeUnless { it is kotlinx.serialization.json.JsonNull } ?: args); null })
        return name
    }

    private fun props(vararg pairs: Pair<String, Any?>): Map<String, JsonElement> =
        pairs.filter { it.second != null }.associate { it.first to jsonOf(it.second) }
}
