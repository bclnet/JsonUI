/*
 * JsonPreview.kt
 * JsonUI
 *
 * Development aid, the counterpart of the Swift `JsonPreview`. Wrap an
 * existing composable to see it next to the JsonUI rendering of the document
 * reflected from its semantics tree, plus the document itself:
 *
 *     @Preview
 *     @Composable
 *     fun LoginPreview() = JsonPreview { LoginForm() }
 *
 * `JsonPreview(json)`, `JsonPreview(document)` and the builder form show a
 * document without an original composable.
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.JsonNodeBuilder
import com.bclnet.jsonui.JsonUIHeader
import com.bclnet.jsonui.compose.reflect.JsonReflection
import com.bclnet.jsonui.compose.reflect.SemanticsReflector
import com.bclnet.jsonui.jsonDocument
import com.bclnet.jsonui.toJsonString

private const val PREVIEW_ROOT_TAG = "jsonui-preview-root"

/**
 * Renders `content`, reflects its semantics tree into a JsonUI document and
 * shows the JsonUI rendering beside it. Clicks, toggles, text and slider
 * changes in the rendered copy drive the original composable through its
 * semantics actions. Name state keys with `Modifier.jsonKey("email")` and
 * actions with `Modifier.jsonAction("save")`.
 */
@Composable
fun JsonPreview(actions: JsonActions = remember { JsonActions() }, content: @Composable () -> Unit) {
    val view = LocalView.current
    var reflection by remember { mutableStateOf<JsonReflection?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    LaunchedEffect(generation) {
        // Semantics are available after layout; wait two frames so the content is measured.
        withFrameNanos { }
        withFrameNanos { }
        reflection = SemanticsReflector.reflect(view, PREVIEW_ROOT_TAG, actions)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.weight(0.6f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).testTag(PREVIEW_ROOT_TAG)) {
                content()
            }
            Box(Modifier.weight(1f).fillMaxSize().border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))) {
                reflection?.let { r ->
                    val model = remember(r) { JsonUIModel(r.document, QuickJsEngine(), r.actions) }
                    DisposableEffect(model) { onDispose { model.close() } }
                    JsonUIView(model)
                }
            }
        }
        Column(
            Modifier.weight(0.4f).fillMaxWidth().padding(top = 8.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            val r = reflection
            if (r == null) {
                Text("Reflecting…", style = MaterialTheme.typography.bodySmall)
            } else {
                if (r.warnings.isNotEmpty()) {
                    Text("Reflection warnings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    r.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("Document", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { generation++ }) { Text("Reflect again") }
                }
                Text(r.document.toJsonString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun JsonPreview(model: JsonUIModel) {
    model.version
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(0.6f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
            JsonUIView(model)
        }
        Column(
            Modifier.weight(0.4f).fillMaxWidth().padding(top = 8.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text("State", style = MaterialTheme.typography.titleSmall)
            Text(model.state.toJsonString(pretty = true), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            if (model.runtime.scriptErrors.isNotEmpty()) {
                Text("Script errors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                model.runtime.scriptErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            Text("Document", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(model.document.toJsonString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun JsonPreview(document: JsonDocument, actions: JsonActions = remember { JsonActions() }) {
    JsonPreview(rememberJsonUIModel(document, actions))
}

/** Parses `json`; a parse error is shown in place of the form. */
@Composable
fun JsonPreview(json: String, actions: JsonActions = remember { JsonActions() }) {
    val result = remember(json) { runCatching { JsonDocument.parse(json) } }
    val document = result.getOrNull()
    if (document != null) {
        JsonPreview(document, actions)
    } else {
        Text(result.exceptionOrNull()?.message ?: "JsonUI: invalid document", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun JsonPreview(actions: JsonActions = remember { JsonActions() }, header: JsonUIHeader = JsonUIHeader(), content: JsonNodeBuilder.() -> Unit) {
    val document = remember { jsonDocument(header, content) }
    JsonPreview(document, actions)
}
