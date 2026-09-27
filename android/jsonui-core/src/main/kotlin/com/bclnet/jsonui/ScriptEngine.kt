/*
 * ScriptEngine.kt
 * JsonUI
 *
 * The scripting contract. QuickJS (Android) implements it in jsonui-compose;
 * both platforms install the shared prelude (scripts/jsonui-prelude.js) and
 * expose the form state through a small JSON-string bridge.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

enum class JsonLogLevel(val id: String) {
    Debug("debug"), Log("log"), Info("info"), Warn("warn"), Error("error");

    companion object {
        fun of(id: String): JsonLogLevel = entries.firstOrNull { it.id == id } ?: Log
    }
}

class JsonScriptException(message: String, cause: Throwable? = null) : RuntimeException("JsonUI script error: $message", cause)

/**
 * The host side of the script bridge. `JsonRuntime` implements it; engines
 * call these methods from the `__jsonui_host` object. All values are JSON
 * strings so that engines which only marshal primitives work unchanged.
 */
interface JsonScriptHost {
    fun scriptGet(path: String): String
    fun scriptSet(path: String, json: String)
    fun scriptHas(key: String): Boolean
    fun scriptKeys(): String
    fun scriptSnapshot(): String
    fun scriptMerge(json: String)
    fun scriptInvoke(name: String, argsJson: String): String
    fun scriptLog(level: String, message: String)
}

/** A JavaScript engine bound to one form. */
interface JsonScriptEngine : AutoCloseable {
    /** Installs the bridge and the prelude. Called once before any other method. */
    fun attach(host: JsonScriptHost)
    /** Loads a document script (`_ui.script`). */
    fun load(script: String)
    /** Evaluates an expression with `locals` in scope and returns its value. */
    fun evaluate(expression: String, locals: Map<String, JsonElement> = emptyMap()): JsonElement
    /** Runs statements with `locals` in scope. */
    fun run(script: String, locals: Map<String, JsonElement> = emptyMap())
    override fun close() {}
}

/**
 * A minimal engine used when no JavaScript engine is available (JVM unit
 * tests). It resolves `state.a.b`, `local.a`, literals and simple `!x`,
 * `x === y`, `x !== y` comparisons; anything else is `null`.
 */
class NoScriptEngine : JsonScriptEngine {
    private var host: JsonScriptHost? = null

    override fun attach(host: JsonScriptHost) { this.host = host }

    override fun load(script: String) {}

    override fun run(script: String, locals: Map<String, JsonElement>) {
        for (statement in script.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val eq = statement.indexOf('=')
            if (eq <= 0 || eq + 1 >= statement.length) continue
            if (statement[eq - 1] == '=' || statement[eq - 1] == '!' || statement[eq + 1] == '=') continue
            val lhs = statement.substring(0, eq).trim()
            val rhs = statement.substring(eq + 1).trim()
            if (!lhs.startsWith("state.")) continue
            host?.scriptSet(lhs.removePrefix("state."), evaluate(rhs, locals).toJsonString())
        }
    }

    override fun evaluate(expression: String, locals: Map<String, JsonElement>): JsonElement {
        val expr = expression.trim()
        if (expr.isEmpty()) return JsonNull
        if (expr.startsWith("!")) return JsonPrimitive(!evaluate(expr.substring(1), locals).isTruthy)
        for (op in listOf("===", "!==", "==", "!=")) {
            val idx = expr.indexOf(" $op ")
            if (idx >= 0) {
                val lhs = evaluate(expr.substring(0, idx), locals)
                val rhs = evaluate(expr.substring(idx + op.length + 2), locals)
                val equal = jsonEquals(lhs, rhs)
                return JsonPrimitive(if (op.startsWith("!")) !equal else equal)
            }
        }
        when (expr) {
            "true" -> return JsonPrimitive(true)
            "false" -> return JsonPrimitive(false)
            "null", "undefined" -> return JsonNull
        }
        expr.toDoubleOrNull()?.let { return jsonNumber(it) }
        if (expr.length >= 2 && ((expr.first() == '\'' && expr.last() == '\'') || (expr.first() == '"' && expr.last() == '"'))) {
            return JsonPrimitive(expr.substring(1, expr.length - 1))
        }
        if (expr.startsWith("state.")) {
            val json = host?.scriptGet(expr.removePrefix("state.")) ?: return JsonNull
            return runCatching { parseJson(json) }.getOrDefault(JsonNull)
        }
        if (expr == "state") {
            val json = host?.scriptSnapshot() ?: return JsonNull
            return runCatching { parseJson(json) }.getOrDefault(JsonNull)
        }
        val path = JsonPath.parse(expr)
        val first = (path.first as? JsonPath.Segment.Key)?.key
        if (first != null && locals.containsKey(first)) return locals.getValue(first).valueAt(path.dropFirst())
        return JsonNull
    }
}

