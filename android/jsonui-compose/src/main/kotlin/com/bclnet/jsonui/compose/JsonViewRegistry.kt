/*
 * JsonViewRegistry.kt
 * JsonUI
 *
 * Host applications register composables for custom node types here.
 */
package com.bclnet.jsonui.compose

import androidx.compose.runtime.Composable
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonNode

typealias JsonComposable = @Composable (node: JsonNode, context: JsonContext, model: JsonUIModel) -> Unit

class JsonViewRegistry {
    private val builders = mutableMapOf<String, JsonComposable>()

    fun register(type: String, builder: JsonComposable): JsonViewRegistry {
        builders[JsonNode.normalize(type)] = builder
        return this
    }

    fun unregister(type: String) { builders.remove(JsonNode.normalize(type)) }

    fun builder(type: String): JsonComposable? = builders[type]

    companion object {
        val shared = JsonViewRegistry()
    }
}
