/*
 * JsonNodeView.kt
 * JsonUI
 *
 * Renders one node: dispatches on its type, then applies modifiers.
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonAction
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonLogLevel
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.arrayValue
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.isNull
import com.bclnet.jsonui.isTruthy
import com.bclnet.jsonui.jsonObjectOf
import com.bclnet.jsonui.stringValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** True inside a subtree that a `disabled` modifier turned off. */
val LocalJsonDisabled = compositionLocalOf { false }

@Composable
fun JsonNodeView(node: JsonNode, context: JsonContext, model: JsonUIModel) {
    if (!context.isVisible(node)) return
    val plan = JsonModifiers.plan(node, context)
    val disabled = plan.disabled
    val childContext = if (disabled) context.child(disabled = true) else context
    val content: @Composable (Modifier) -> Unit = { modifier -> NodeContent(node, childContext, model, modifier) }
    plan.onAppear?.let { action -> LaunchedEffect(node) { context.perform(action) } }
    val styled: @Composable (Modifier) -> Unit = if (plan.textStyle != null || plan.color != null) {
        { modifier ->
            CompositionLocalProvider(
                LocalTextStyle provides (plan.textStyle ?: LocalTextStyle.current),
                LocalContentColor provides (plan.color ?: LocalContentColor.current),
            ) { content(modifier) }
        }
    } else content
    if (disabled) {
        CompositionLocalProvider(LocalJsonDisabled provides true) { styled(plan.modifier) }
    } else {
        styled(plan.modifier)
    }
}

@Composable
private fun NodeContent(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
    when (node.kind) {
        JsonNode.Kind.Text -> JsonTextViews.Text(node, context, modifier)
        JsonNode.Kind.Label -> JsonTextViews.Label(node, context, modifier)
        JsonNode.Kind.Image -> JsonTextViews.Image(node, context, modifier)
        JsonNode.Kind.Link -> JsonTextViews.Link(node, context, modifier)
        JsonNode.Kind.ProgressView -> JsonTextViews.Progress(node, context, modifier)
        JsonNode.Kind.TextField -> JsonInputViews.TextField(node, context, modifier, secure = false)
        JsonNode.Kind.SecureField -> JsonInputViews.TextField(node, context, modifier, secure = true)
        JsonNode.Kind.TextEditor -> JsonInputViews.TextEditor(node, context, modifier)
        JsonNode.Kind.Toggle -> JsonInputViews.Toggle(node, context, model, modifier)
        JsonNode.Kind.Picker -> JsonInputViews.Picker(node, context, model, modifier)
        JsonNode.Kind.DatePicker -> JsonInputViews.DatePicker(node, context, modifier)
        JsonNode.Kind.Slider -> JsonInputViews.Slider(node, context, modifier)
        JsonNode.Kind.Stepper -> JsonInputViews.Stepper(node, context, modifier)
        JsonNode.Kind.Button -> JsonInputViews.Button(node, context, model, modifier)
        JsonNode.Kind.Form -> JsonContainerViews.Form(node, context, model, modifier)
        JsonNode.Kind.Section -> JsonContainerViews.Section(node, context, model, modifier)
        JsonNode.Kind.List -> JsonContainerViews.Form(node, context, model, modifier)
        JsonNode.Kind.VStack -> JsonContainerViews.VStack(node, context, model, modifier)
        JsonNode.Kind.HStack -> JsonContainerViews.HStack(node, context, model, modifier)
        JsonNode.Kind.ZStack -> JsonContainerViews.ZStack(node, context, model, modifier)
        JsonNode.Kind.ScrollView -> JsonContainerViews.ScrollView(node, context, model, modifier)
        JsonNode.Kind.Group -> Box(modifier) { JsonContainerViews.Children(node.content, context, model) }
        JsonNode.Kind.NavigationView -> JsonContainerViews.NavigationView(node, context, model, modifier)
        JsonNode.Kind.Spacer -> Spacer(modifier.then(node["minLength"].doubleValue?.let { Modifier.defaultMinSize(it.dp, it.dp) } ?: Modifier.fillMaxWidth().weight()))
        JsonNode.Kind.Divider -> HorizontalDivider(modifier)
        JsonNode.Kind.ForEach -> JsonContainerViews.ForEach(node, context, model)
        JsonNode.Kind.If -> JsonContainerViews.Conditional(node, context, model)
        null -> {
            val builder = model.registry.builder(node.type)
            if (builder != null) builder(node, context, model)
            else Text("JsonUI: unknown view \"${node.type}\"", modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Spacers cannot know whether they are in a Row or Column; parents grant weight through [JsonContainerViews]. */
private fun Modifier.weight(): Modifier = this

/** Renders a label value that may be a string (dynamic) or a nested node. */
@Composable
fun JsonLabel(value: JsonElement, context: JsonContext, model: JsonUIModel, modifier: Modifier = Modifier) {
    val node = JsonNode.fromValue(value)
    if (node != null) JsonNodeView(node, context, model)
    else Text(context.resolve(value).stringValue ?: "", modifier)
}

object JsonModifiers {
    /** The shorthand modifier properties, in application order (matches the Swift renderer). */
    val shorthand = listOf("padding", "frame", "font", "bold", "italic", "foregroundColor", "background", "cornerRadius", "border", "opacity", "disabled", "onAppear", "onTap", "accessibilityLabel")

    class Plan(
        val modifier: Modifier,
        val textStyle: androidx.compose.ui.text.TextStyle?,
        val color: androidx.compose.ui.graphics.Color?,
        val disabled: Boolean,
        val onAppear: JsonAction?,
    )

    /**
     * Turns the node's modifiers into a Compose [Modifier]. SwiftUI modifiers
     * wrap from the inside out while Compose modifiers apply from the outside
     * in, so the list is applied in reverse.
     */
    @Composable
    fun plan(node: JsonNode, context: JsonContext): Plan {
        val entries = mutableListOf<Pair<String, JsonElement>>()
        for (modifier in node["modifiers"].arrayValue ?: emptyList()) {
            val type = modifier["type"].stringValue ?: continue
            entries.add(type to modifier)
        }
        for (key in shorthand) {
            if (!node.has(key)) continue
            if ((key == "bold" || key == "italic") && node.has("font")) continue
            entries.add(key to shorthandProps(key, node))
        }
        var modifier: Modifier = Modifier
        var textStyle: androidx.compose.ui.text.TextStyle? = null
        var color: androidx.compose.ui.graphics.Color? = null
        var disabled = context.isDisabled
        var onAppear: JsonAction? = null
        for ((type, props) in entries.asReversed()) {
            when (type) {
                "padding" -> modifier = modifier.padding(paddingValues(props))
                "frame" -> modifier = modifier.frame(props)
                "font" -> {
                    var style = JsonStyles.textStyle(props["font"], context) ?: LocalTextStyle.current
                    if (context.resolve(props["bold"]).isTruthy) style = style.copy(fontWeight = FontWeight.Bold)
                    if (context.resolve(props["italic"]).isTruthy) style = style.copy(fontStyle = FontStyle.Italic)
                    textStyle = style
                }
                "bold" -> if (context.resolve(props).isTruthy) textStyle = (textStyle ?: LocalTextStyle.current).copy(fontWeight = FontWeight.Bold)
                "italic" -> if (context.resolve(props).isTruthy) textStyle = (textStyle ?: LocalTextStyle.current).copy(fontStyle = FontStyle.Italic)
                "foregroundColor" -> color = JsonStyles.color(props["color"], context) ?: color
                "background" -> JsonStyles.color(props["color"], context)?.let { modifier = modifier.background(it) }
                "cornerRadius" -> modifier = modifier.clip(RoundedCornerShape((context.resolve(props["radius"]).doubleValue ?: 0.0).dp))
                "border" -> modifier = modifier.border((props["width"].doubleValue ?: 1.0).dp, JsonStyles.color(props["color"], context) ?: androidx.compose.ui.graphics.Color.Gray)
                "opacity" -> modifier = modifier.alpha((context.resolve(props["value"]).doubleValue ?: 1.0).toFloat())
                "disabled" -> if (context.resolve(props["value"]).isTruthy) disabled = true
                "onAppear" -> onAppear = JsonAction.of(props["action"])
                "onTap" -> JsonAction.of(props["action"])?.let { action -> modifier = modifier.clickable { context.perform(action) } }
                "accessibilityLabel" -> context.resolve(props["label"]).stringValue?.let { label -> modifier = modifier.semantics { contentDescription = label } }
                else -> context.log(JsonLogLevel.Warn, "JsonUI: unknown modifier \"$type\"")
            }
        }
        return Plan(modifier, textStyle, color, disabled, onAppear)
    }

    private fun shorthandProps(key: String, node: JsonNode): JsonElement {
        val value = node[key]
        return when (key) {
            "padding" -> when {
                value is JsonObject -> value
                value.doubleValue != null && value != JsonPrimitive(true) -> jsonObjectOf("length" to value.doubleValue)
                else -> JsonObject(emptyMap())
            }
            "font" -> JsonObject(mapOf("font" to value) + listOfNotNull(node.props["bold"]?.let { "bold" to it }, node.props["italic"]?.let { "italic" to it }))
            "frame", "border", "bold", "italic" -> value
            "foregroundColor", "background" -> jsonObjectOf("color" to value)
            "cornerRadius" -> jsonObjectOf("radius" to value)
            "opacity", "disabled" -> jsonObjectOf("value" to value)
            "onAppear", "onTap" -> jsonObjectOf("action" to value)
            "accessibilityLabel" -> jsonObjectOf("label" to value)
            else -> value
        }
    }

    private fun paddingValues(props: JsonElement): PaddingValues {
        val length = props["length"].doubleValue
        val all = (length ?: 16.0).dp
        if (props["top"].doubleValue != null || props["leading"].doubleValue != null || props["bottom"].doubleValue != null || props["trailing"].doubleValue != null) {
            return PaddingValues(
                start = (props["leading"].doubleValue ?: 0.0).dp, top = (props["top"].doubleValue ?: 0.0).dp,
                end = (props["trailing"].doubleValue ?: 0.0).dp, bottom = (props["bottom"].doubleValue ?: 0.0).dp)
        }
        return when (props["edges"].stringValue) {
            "horizontal" -> PaddingValues(horizontal = all)
            "vertical" -> PaddingValues(vertical = all)
            "top" -> PaddingValues(top = all)
            "bottom" -> PaddingValues(bottom = all)
            "leading" -> PaddingValues(start = all)
            "trailing" -> PaddingValues(end = all)
            else -> PaddingValues(all)
        }
    }

    private fun Modifier.frame(props: JsonElement): Modifier {
        var m = this
        fun isInfinity(v: JsonElement) = v.stringValue?.lowercase().let { it == "infinity" || it == "max" }
        props["width"].doubleValue?.let { m = m.width(it.dp) }
        props["height"].doubleValue?.let { m = m.height(it.dp) }
        val maxWidth = props["maxWidth"]
        val maxHeight = props["maxHeight"]
        if (!maxWidth.isNull) { if (isInfinity(maxWidth)) m = m.fillMaxWidth() else maxWidth.doubleValue?.let { m = m.widthIn(max = it.dp) } }
        if (!maxHeight.isNull) { if (isInfinity(maxHeight)) m = m.fillMaxHeight() else maxHeight.doubleValue?.let { m = m.heightIn(max = it.dp) } }
        props["minWidth"].doubleValue?.let { m = m.widthIn(min = it.dp) }
        props["minHeight"].doubleValue?.let { m = m.heightIn(min = it.dp) }
        return m
    }
}
