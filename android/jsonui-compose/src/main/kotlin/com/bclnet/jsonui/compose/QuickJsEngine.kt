/*
 * QuickJsEngine.kt
 * JsonUI
 *
 * QuickJS implementation of JsonScriptEngine (app.cash.quickjs). The form
 * state is exposed through the `__jsonui_host` bridge as JSON strings; the
 * shared prelude (scripts/jsonui-prelude.js) builds the `state` proxy on top.
 *
 * QuickJS instances are single threaded: create, use and close the engine on
 * the same thread (JsonUI does everything on the main thread).
 */
package com.bclnet.jsonui.compose

import android.util.Log
import app.cash.quickjs.QuickJs
import app.cash.quickjs.QuickJsException
import com.bclnet.jsonui.JsonScriptEngine
import com.bclnet.jsonui.JsonScriptException
import com.bclnet.jsonui.JsonScriptHost
import com.bclnet.jsonui.ScriptPrelude
import com.bclnet.jsonui.escapeJson
import com.bclnet.jsonui.parseJson
import com.bclnet.jsonui.toJsonString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** The bridge interface QuickJS binds to `__jsonui_host`. Only primitive and String types cross the JNI boundary. */
interface QuickJsHostBridge {
    fun get(path: String): String
    fun set(path: String, json: String)
    fun has(key: String): Boolean
    fun keys(): String
    fun snapshot(): String
    fun merge(json: String)
    fun invoke(name: String, argsJson: String): String
    fun log(level: String, message: String)
}

class QuickJsEngine : JsonScriptEngine {
    private val quickJs: QuickJs = QuickJs.create()
    private var host: JsonScriptHost? = null
    private var closed = false

    override fun attach(host: JsonScriptHost) {
        this.host = host
        val bridge = object : QuickJsHostBridge {
            override fun get(path: String): String = this@QuickJsEngine.host?.scriptGet(path) ?: "null"
            override fun set(path: String, json: String) { this@QuickJsEngine.host?.scriptSet(path, json) }
            override fun has(key: String): Boolean = this@QuickJsEngine.host?.scriptHas(key) ?: false
            override fun keys(): String = this@QuickJsEngine.host?.scriptKeys() ?: "[]"
            override fun snapshot(): String = this@QuickJsEngine.host?.scriptSnapshot() ?: "{}"
            override fun merge(json: String) { this@QuickJsEngine.host?.scriptMerge(json) }
            override fun invoke(name: String, argsJson: String): String = this@QuickJsEngine.host?.scriptInvoke(name, argsJson) ?: "null"
            override fun log(level: String, message: String) {
                this@QuickJsEngine.host?.scriptLog(level, message) ?: Log.println(Log.INFO, "JsonUI", message)
            }
        }
        quickJs.set("__jsonui_host", QuickJsHostBridge::class.java, bridge)
        checked("prelude") { quickJs.evaluate(ScriptPrelude.SOURCE, "jsonui-prelude.js") }
    }

    override fun load(script: String) {
        checked("script") { quickJs.evaluate(script, "document.js") }
    }

    override fun evaluate(expression: String, locals: Map<String, JsonElement>): JsonElement {
        val call = "__jsonui_eval(${escapeJson(expression)}, ${escapeJson(JsonObject(locals).toJsonString())})"
        val result = checked(expression) { quickJs.evaluate(call) }
        return parseResult(result)
    }

    override fun run(script: String, locals: Map<String, JsonElement>) {
        val call = "__jsonui_run(${escapeJson(script)}, ${escapeJson(JsonObject(locals).toJsonString())})"
        checked(script) { quickJs.evaluate(call) }
    }

    override fun close() {
        if (!closed) {
            closed = true
            quickJs.close()
        }
    }

    private fun <T> checked(what: String, body: () -> T): T {
        if (closed) throw JsonScriptException("engine is closed")
        return try {
            body()
        } catch (e: QuickJsException) {
            throw JsonScriptException("${e.message} in: ${what.take(120)}", e)
        }
    }

    private fun parseResult(result: Any?): JsonElement = when (result) {
        null -> JsonNull
        is String -> runCatching { parseJson(result) }.getOrDefault(JsonNull)
        else -> runCatching { parseJson(result.toString()) }.getOrDefault(JsonNull)
    }
}
