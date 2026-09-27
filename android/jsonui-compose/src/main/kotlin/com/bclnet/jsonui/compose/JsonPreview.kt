/*
 * JsonPreview.kt
 * JsonUI
 *
 * Development aid that shows the rendered form above its live state and
 * JSON, the counterpart of the Swift `JsonPreview`.
 *
 *     @Preview
 *     @Composable
 *     fun LoginPreview() = JsonPreview(loginJson)
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.JsonNodeBuilder
import com.bclnet.jsonui.JsonUIHeader
import com.bclnet.jsonui.jsonDocument
import com.bclnet.jsonui.toJsonString

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
