/*
 * QuickJsEngine.kt
 * JsonUI
 *
 * QuickJS implementation of JsonScriptEngine (wang.harlon.quickjs, whose
 * native library is built for 16 KB pages). The form state is exposed
 * through the `__jsonui_host` bridge as JSON strings; the shared prelude
 * (scripts/jsonui-prelude.js) builds the `state` proxy on top.
 *
 * QuickJS instances are single threaded: create, use and close the engine on
 * the same thread (JsonUI does everything on the main thread).
 */
package com.bclnet.jsonui.compose

import android.util.Log
import com.bclnet.jsonui.JsonScriptEngine
import com.bclnet.jsonui.JsonScriptException
import com.bclnet.jsonui.JsonScriptHost
import com.bclnet.jsonui.ScriptPrelude
import com.bclnet.jsonui.escapeJson
import com.bclnet.jsonui.parseJson
import com.bclnet.jsonui.toJsonString
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class QuickJsEngine : JsonScriptEngine {
    private val quickJs: QuickJSContext = run { QuickJSLoader.init(); QuickJSContext.create() }
    private var host: JsonScriptHost? = null
    private var closed = false

    override fun attach(host: JsonScriptHost) {
        this.host = host
        // `__jsonui_host`: only strings and booleans cross the JNI boundary.
        val bridge = quickJs.createNewJSObject()
        fun bind(name: String, body: (Array<out Any?>) -> Any?) = bridge.setProperty(name, JSCallFunction { args -> body(args) })
        fun Array<out Any?>.text(index: Int): String = getOrNull(index)?.toString() ?: ""
        bind("get") { args -> this.host?.scriptGet(args.text(0)) ?: "null" }
        bind("set") { args -> this.host?.scriptSet(args.text(0), args.text(1)); null }
        bind("has") { args -> this.host?.scriptHas(args.text(0)) ?: false }
        bind("keys") { this.host?.scriptKeys() ?: "[]" }
        bind("snapshot") { this.host?.scriptSnapshot() ?: "{}" }
        bind("merge") { args -> this.host?.scriptMerge(args.text(0)); null }
        bind("invoke") { args -> this.host?.scriptInvoke(args.text(0), args.text(1)) ?: "null" }
        bind("log") { args -> this.host?.scriptLog(args.text(0), args.text(1)) ?: Log.println(Log.INFO, "JsonUI", args.text(1)); null }
        // The global object belongs to the context; only the bridge handle is ours to release.
        quickJs.globalObject.setProperty("__jsonui_host", bridge)
        bridge.release()
        checked("prelude") { evaluate(ScriptPrelude.SOURCE, "jsonui-prelude.js") }
    }

    override fun load(script: String) {
        checked("script") { evaluate(script, "document.js") }
    }

    override fun evaluate(expression: String, locals: Map<String, JsonElement>): JsonElement {
        val call = "__jsonui_eval(${escapeJson(expression)}, ${escapeJson(JsonObject(locals).toJsonString())})"
        val result = checked(expression) { evaluate(call, "expression.js") }
        return parseResult(result)
    }

    override fun run(script: String, locals: Map<String, JsonElement>) {
        val call = "__jsonui_run(${escapeJson(script)}, ${escapeJson(JsonObject(locals).toJsonString())})"
        checked(script) { evaluate(call, "action.js") }
    }

    override fun close() {
        if (!closed) {
            closed = true
            quickJs.destroy()
        }
    }

    /** Evaluates and hands back plain values only: an object result is released here, never leaked. */
    private fun evaluate(source: String, name: String): Any? {
        val result = quickJs.evaluate(source, name)
        if (result is JSObject) { result.release(); return null }
        return result
    }

    private fun <T> checked(what: String, body: () -> T): T {
        if (closed) throw JsonScriptException("engine is closed")
        return try {
            body()
        } catch (e: QuickJSException) {
            throw JsonScriptException("${e.message} in: ${what.take(120)}", e)
        }
    }

    private fun parseResult(result: Any?): JsonElement = when (result) {
        null -> JsonNull
        is String -> runCatching { parseJson(result) }.getOrDefault(JsonNull)
        else -> runCatching { parseJson(result.toString()) }.getOrDefault(JsonNull)
    }
}
