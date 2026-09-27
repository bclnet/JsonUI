/*
 * JsonAction.kt
 * JsonUI
 *
 * An action is data: a host action name, a script, a state assignment or a
 * sequence of actions.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class JsonAction {
    /** Invokes an action registered by the embedding application. */
    data class Host(val name: String, val args: Map<String, JsonElement> = emptyMap()) : JsonAction()
    /** Runs JavaScript in the script engine. */
    data class Script(val script: String) : JsonAction()
    /** Assigns dynamic values to state keys. */
    data class Set(val values: Map<String, JsonElement>) : JsonAction()
    /** Runs several actions in order. */
    data class Sequence(val actions: List<JsonAction>) : JsonAction()

    val value: JsonElement
        get() = when (this) {
            is Host -> if (args.isEmpty()) JsonPrimitive(name) else jsonObjectOf("name" to name, "args" to JsonObject(args))
            is Script -> JsonPrimitive("$SCRIPT_PREFIX $script")
            is Set -> jsonObjectOf("set" to JsonObject(values))
            is Sequence -> JsonArray(actions.map { it.value })
        }

    companion object {
        const val SCRIPT_PREFIX = "js:"

        fun of(value: JsonElement): JsonAction? = when (value) {
            is JsonPrimitive -> if (value.isString) of(value.content) else null
            is JsonArray -> Sequence(value.mapNotNull { of(it) })
            is JsonObject -> when {
                value["script"]?.stringValue != null -> Script(value["script"]!!.stringValue!!)
                value["name"]?.stringValue != null -> Host(value["name"]!!.stringValue!!, value["args"]?.objectValue ?: emptyMap())
                value["set"]?.objectValue != null -> Set(value["set"]!!.objectValue!!)
                else -> null
            }
            else -> null
        }

        fun of(text: String): JsonAction? {
            val trimmed = text.trim()
            return when {
                trimmed.lowercase().startsWith(SCRIPT_PREFIX) -> Script(trimmed.substring(SCRIPT_PREFIX.length).trim())
                trimmed.isEmpty() -> null
                else -> Host(trimmed)
            }
        }
    }
}

/**
 * A host action handler. `args` are already resolved (bindings and
 * expressions evaluated). The returned value is handed back to scripts that
 * used `host.invoke`.
 */
fun interface JsonActionHandler {
    fun invoke(name: String, args: JsonElement, context: JsonContext): JsonElement?
}

/** Registry of host actions available to a document. */
class JsonActions {
    private val handlers = mutableMapOf<String, JsonActionHandler>()

    /** Called for action names that have no specific handler. */
    var fallback: JsonActionHandler? = null

    fun register(name: String, handler: JsonActionHandler): JsonActions { handlers[name] = handler; return this }

    fun register(name: String, handler: (args: JsonElement) -> Unit): JsonActions =
        register(name, JsonActionHandler { _, args, _ -> handler(args); null })

    fun unregister(name: String) { handlers.remove(name) }

    operator fun contains(name: String): Boolean = handlers.containsKey(name)

    fun invoke(name: String, args: JsonElement, context: JsonContext): JsonElement? {
        handlers[name]?.let { return it.invoke(name, args, context) }
        fallback?.let { return it.invoke(name, args, context) }
        context.log(JsonLogLevel.Warn, "JsonUI: no host action registered for \"$name\"")
        return null
    }
}
