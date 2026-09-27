/*
 * ContainerViews.kt
 * JsonUI
 *
 * Form, Section, stacks, ScrollView, NavigationView, ForEach and If.
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.JsonScope
import com.bclnet.jsonui.arrayValue
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.stringValue
import kotlinx.serialization.json.JsonObject

object JsonContainerViews {
    /** Renders a list of child nodes inline. */
    @Composable
    fun Children(nodes: List<JsonNode>, context: JsonContext, model: JsonUIModel) {
        nodes.forEach { JsonNodeView(it, context, model) }
    }

    @Composable
    fun Form(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Children(node.content, context, model)
        }
    }

    @Composable
    fun Section(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (node.has("header")) {
                val header = node["header"]
                if (header is JsonObject) JsonLabel(header, context, model)
                else Text((context.resolve(header).stringValue ?: "").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Children(node.content, context, model)
                }
            }
            if (node.has("footer")) {
                val footer = node["footer"]
                if (footer is JsonObject) JsonLabel(footer, context, model)
                else Text(context.resolve(footer).stringValue ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    fun VStack(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val spacing = node["spacing"].doubleValue
        Column(
            modifier,
            horizontalAlignment = JsonStyles.horizontalAlignment(node["alignment"].stringValue),
            verticalArrangement = if (spacing != null) Arrangement.spacedBy(spacing.dp) else Arrangement.spacedBy(8.dp),
        ) { Children(node.content, context, model) }
    }

    @Composable
    fun HStack(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val spacing = node["spacing"].doubleValue
        Row(
            modifier,
            verticalAlignment = JsonStyles.verticalAlignment(node["alignment"].stringValue),
            horizontalArrangement = if (spacing != null) Arrangement.spacedBy(spacing.dp) else Arrangement.spacedBy(8.dp),
        ) {
            // Spacers inside a row expand to fill the free space, as in SwiftUI.
            node.content.forEach { child ->
                if (child.kind == JsonNode.Kind.Spacer && !child.has("minLength")) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                else JsonNodeView(child, context, model)
            }
        }
    }

    @Composable
    fun ZStack(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        Box(modifier, contentAlignment = JsonStyles.alignment(node["alignment"].stringValue)) { Children(node.content, context, model) }
    }

    @Composable
    fun ScrollView(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        if (node["axis"].stringValue == "horizontal") {
            Row(modifier.horizontalScroll(rememberScrollState())) { Children(node.content, context, model) }
        } else {
            Column(modifier.verticalScroll(rememberScrollState())) { Children(node.content, context, model) }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NavigationView(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val title = context.string(node, "title")
        Scaffold(modifier.fillMaxSize(), topBar = { if (title != null) TopAppBar(title = { Text(title) }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) { Children(node.content, context, model) }
        }
    }

    // MARK: - ForEach

    @Composable
    fun ForEach(node: JsonNode, context: JsonContext, model: JsonUIModel) {
        val itemName = node["item"].stringValue ?: "item"
        val indexName = node["index"].stringValue ?: "index"
        val basePath = context.bindingPath(node, "data")
        val items = context.resolve(node, "data").arrayValue ?: emptyList()
        val template = node.content
        items.forEachIndexed { index, item ->
            val scope = JsonScope(itemName, indexName, basePath?.appending(index), index, item)
            Children(template, context.child(scope), model)
        }
    }

    // MARK: - If

    @Composable
    fun Conditional(node: JsonNode, context: JsonContext, model: JsonUIModel) {
        if (context.bool(node, "condition")) Children(node.content, context, model)
        else Children(node.nodes("else"), context, model)
    }
}
