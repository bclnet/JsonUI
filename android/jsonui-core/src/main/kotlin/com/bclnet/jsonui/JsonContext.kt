/*
 * JsonContext.kt
 * JsonUI
 *
 * `JsonRuntime` owns everything shared by one rendered document: the state
 * store, the script engine, host actions and strings. `JsonContext` is the
 * per-subtree view of the runtime, carrying the `ForEach` scopes that map
 * item variables (`phone`, `i`) to state paths and script locals.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class JsonRuntime(
    document: JsonDocument,
    val engine: JsonScriptEngine = NoScriptEngine(),
    val actions: JsonActions = JsonActions(),
    state: Map<String, JsonElement>? = null,
) : JsonScriptHost, AutoCloseable {
    val store: JsonStore = JsonStore(document.header.state + (state ?: emptyMap()))
    var strings: Map<String, String> = document.header.strings

    /** Optional fallback localizer used when a `@key` is not in `strings`. */
    var localizer: ((String) -> String?)? = null

    var logger: (JsonLogLevel, String) -> Unit = { level, message -> println("[JsonUI:${level.id}] $message") }

    private val _scriptErrors = mutableListOf<String>()

    /** Errors raised by scripts and expressions, most recent last. */
    val scriptErrors: List<String> get() = _scriptErrors.toList()

    init {
        try {
            engine.attach(this)
            document.header.script?.takeIf { it.isNotBlank() }?.let { engine.load(it) }
        } catch (e: Exception) {
            report(e)
        }
    }

    constructor(engine: JsonScriptEngine = NoScriptEngine(), actions: JsonActions = JsonActions(), state: Map<String, JsonElement> = emptyMap()) :
        this(JsonDocument(JsonUIHeader(state = state), JsonNode(JsonNode.Kind.Group)), engine, actions)

    val context: JsonContext get() = JsonContext(this)

    internal fun report(error: Throwable) {
        val message = error.message ?: error.toString()
        _scriptErrors.add(message)
        logger(JsonLogLevel.Error, message)
    }

    override fun close() { engine.close() }

    // MARK: - JsonScriptHost

    override fun scriptGet(path: String): String = store.get(path).toJsonString()
    override fun scriptSet(path: String, json: String) = store.set(runCatching { parseJson(json) }.getOrDefault(JsonNull), path)
    override fun scriptHas(key: String): Boolean = store.has(key)
    override fun scriptKeys(): String = JsonArray(store.keys.map { JsonPrimitive(it) }).toJsonString()
    override fun scriptSnapshot(): String = store.snapshot.toJsonString()
    override fun scriptMerge(json: String) = store.merge(runCatching { parseJson(json) }.getOrNull()?.objectValue ?: emptyMap())
    override fun scriptInvoke(name: String, argsJson: String): String {
        val args = runCatching { parseJson(argsJson) }.getOrDefault(JsonNull)
        return (actions.invoke(name, args, context) ?: JsonNull).toJsonString()
    }
    override fun scriptLog(level: String, message: String) = logger(JsonLogLevel.of(level), message)
}

data class JsonScope(
    /** Name of the item variable inside the `ForEach` template. */
    val itemName: String,
    /** Name of the index variable. */
    val indexName: String,
    /** State path of the array element, so `$item.field` bindings write back. */
    val basePath: JsonPath?,
    val index: Int,
    val item: JsonElement,
)

class JsonContext(
    val runtime: JsonRuntime,
    val scopes: List<JsonScope> = emptyList(),
    /** Whether an ancestor disabled interaction. */
    val isDisabled: Boolean = false,
) {
    val store: JsonStore get() = runtime.store

    fun child(scope: JsonScope): JsonContext = JsonContext(runtime, scopes + scope, isDisabled)

    fun child(disabled: Boolean): JsonContext = JsonContext(runtime, scopes, isDisabled || disabled)

    fun log(level: JsonLogLevel, message: String) = runtime.logger(level, message)

    /** Script locals contributed by the enclosing `ForEach` scopes. */
    val locals: Map<String, JsonElement>
        get() {
            val locals = linkedMapOf<String, JsonElement>()
            for (scope in scopes) {
                locals[scope.itemName] = scope.item
                locals[scope.indexName] = JsonPrimitive(scope.index)
            }
            return locals
        }

    // MARK: - Bindings

    /**
     * Maps a binding path to a state path, replacing `ForEach` item variables
     * with their element paths (`phone.label` → `phones[0].label`). Returns
     * null for read-only scope values.
     */
    fun statePath(path: JsonPath): JsonPath? {
        val first = (path.first as? JsonPath.Segment.Key)?.key ?: return path
        val scope = scopes.lastOrNull { it.itemName == first } ?: return path
        return scope.basePath?.appending(path.dropFirst())
    }

    /** The state path bound by `value` (`"$email"` / `{"$bind": ...}`), if any. */
    fun bindingPath(value: JsonElement): JsonPath? = value.dynamic.bindingPath?.let { statePath(it) }

    fun bindingPath(node: JsonNode, key: String): JsonPath? = bindingPath(node[key])

    fun get(path: JsonPath): JsonElement {
        statePath(path)?.let { return store.get(it) }
        val first = (path.first as? JsonPath.Segment.Key)?.key ?: return JsonNull
        val scope = scopes.lastOrNull { it.itemName == first } ?: return JsonNull
        return scope.item.valueAt(path.dropFirst())
    }

    fun set(value: JsonElement, path: JsonPath) {
        val statePath = statePath(path)
        if (statePath == null) {
            log(JsonLogLevel.Warn, "JsonUI: cannot write to read-only scope value $path")
            return
        }
        store.set(value, statePath)
    }

    // MARK: - Resolution

    /** Resolves a dynamic value to a concrete JSON value. */
    fun resolve(value: JsonElement): JsonElement = resolve(value.dynamic)

    fun resolve(dynamic: DynamicValue): JsonElement = when (dynamic) {
        is DynamicValue.Literal -> when (val v = dynamic.literal) {
            is JsonArray -> JsonArray(v.map { resolve(it) })
            is JsonObject -> JsonObject(v.mapValues { resolve(it.value) })
            else -> v
        }
        is DynamicValue.Binding -> get(dynamic.path)
        is DynamicValue.Expression -> evaluate(dynamic.expression)
        is DynamicValue.Localized -> JsonPrimitive(localized(dynamic.key))
        is DynamicValue.Template -> JsonPrimitive(dynamic.parts.joinToString("") {
            when (it) {
                is DynamicValue.TemplatePart.Text -> it.text
                is DynamicValue.TemplatePart.Expression -> evaluate(it.expression).stringValue ?: ""
            }
        })
    }

    fun resolve(node: JsonNode, key: String): JsonElement = resolve(node[key])

    fun string(node: JsonNode, key: String): String? = resolve(node, key).stringValue
    fun string(node: JsonNode, key: String, default: String): String = string(node, key) ?: default
    fun bool(node: JsonNode, key: String, default: Boolean = false): Boolean {
        val v = resolve(node, key)
        return if (v.isNull) default else v.boolValue ?: v.isTruthy
    }
    fun double(node: JsonNode, key: String): Double? = resolve(node, key).doubleValue
    fun double(node: JsonNode, key: String, default: Double): Double = double(node, key) ?: default
    fun int(node: JsonNode, key: String): Int? = resolve(node, key).intValue

    fun localized(key: String): String = runtime.strings[key] ?: runtime.localizer?.invoke(key) ?: key

    /** Whether the node should be rendered. */
    fun isVisible(node: JsonNode): Boolean = !bool(node, "hidden")

    /** Whether the node (or an ancestor) is disabled. */
    fun isDisabled(node: JsonNode): Boolean = isDisabled || bool(node, "disabled")

    fun evaluate(expression: String): JsonElement = try {
        runtime.engine.evaluate(expression, locals)
    } catch (e: Exception) {
        runtime.report(e)
        JsonNull
    }

    // MARK: - Actions

    fun action(node: JsonNode, key: String): JsonAction? = JsonAction.of(node[key])

    fun hasAction(node: JsonNode, key: String): Boolean = action(node, key) != null

    /** Performs the action stored under `key` of `node`, if any. */
    fun perform(node: JsonNode, key: String) { action(node, key)?.let { perform(it) } }

    fun perform(action: JsonAction) {
        when (action) {
            is JsonAction.Host -> runtime.actions.invoke(action.name, JsonObject(action.args.mapValues { resolve(it.value) }), this)
            is JsonAction.Script -> try { runtime.engine.run(action.script, locals) } catch (e: Exception) { runtime.report(e) }
            is JsonAction.Set -> action.values.forEach { (key, value) -> set(resolve(value), JsonPath.parse(key)) }
            is JsonAction.Sequence -> action.actions.forEach { perform(it) }
        }
    }
}
