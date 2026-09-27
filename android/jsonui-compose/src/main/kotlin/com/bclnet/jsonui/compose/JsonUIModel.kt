/*
 * JsonUIModel.kt
 * JsonUI
 *
 * The state holder behind a rendered document, and the root composable.
 */
package com.bclnet.jsonui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bclnet.jsonui.JsonActionHandler
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.JsonRuntime
import com.bclnet.jsonui.JsonScriptEngine
import com.bclnet.jsonui.JsonStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Owns the runtime (state, script engine, host actions) for one document and
 * exposes a [version] counter that composables read to recompose on changes.
 */
class JsonUIModel(
    val document: JsonDocument,
    engine: JsonScriptEngine = QuickJsEngine(),
    actions: JsonActions = JsonActions(),
    val registry: JsonViewRegistry = JsonViewRegistry.shared,
    state: Map<String, JsonElement>? = null,
) : AutoCloseable {
    val runtime: JsonRuntime = JsonRuntime(document, engine, actions, state)

    /** Incremented on every state change; read it in a composable to subscribe. */
    var version: Int by mutableIntStateOf(0)
        private set

    private val listener = runtime.store.addListener { version++ }

    val context: JsonContext get() = runtime.context
    val store: JsonStore get() = runtime.store
    val actions: JsonActions get() = runtime.actions

    /** The current state as a JSON object. */
    val state: JsonObject get() = runtime.store.snapshot

    /** Registers a host action. */
    fun on(name: String, handler: JsonActionHandler): JsonUIModel { runtime.actions.register(name, handler); return this }
    fun on(name: String, handler: (JsonElement) -> Unit): JsonUIModel { runtime.actions.register(name, handler); return this }

    override fun close() {
        runtime.store.removeListener(listener)
        runtime.close()
    }

    companion object {
        fun parse(json: String, engine: JsonScriptEngine = QuickJsEngine(), actions: JsonActions = JsonActions(), registry: JsonViewRegistry = JsonViewRegistry.shared): JsonUIModel =
            JsonUIModel(JsonDocument.parse(json), engine, actions, registry)
    }
}

/**
 * Remembers a [JsonUIModel] for `document` and closes its script engine when
 * the composable leaves the composition.
 */
@Composable
fun rememberJsonUIModel(
    document: JsonDocument,
    actions: JsonActions = remember { JsonActions() },
    registry: JsonViewRegistry = JsonViewRegistry.shared,
    engine: () -> JsonScriptEngine = { QuickJsEngine() },
): JsonUIModel {
    val model = remember(document, actions, registry) { JsonUIModel(document, engine(), actions, registry) }
    DisposableEffect(model) { onDispose { model.close() } }
    return model
}

/**
 * Renders a JsonUI document.
 *
 *     val model = rememberJsonUIModel(JsonDocument.parse(json))
 *     JsonUIView(model)
 */
@Composable
fun JsonUIView(model: JsonUIModel) {
    // Reading the version subscribes this composable (and its subtree) to state changes.
    model.version
    JsonNodeView(model.document.root, model.context, model)
}

@Composable
fun JsonUIView(document: JsonDocument, actions: JsonActions = remember { JsonActions() }) {
    JsonUIView(rememberJsonUIModel(document, actions))
}
