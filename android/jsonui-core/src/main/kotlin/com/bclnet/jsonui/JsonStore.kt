/*
 * JsonStore.kt
 * JsonUI
 *
 * The form state: an observable key/value store keyed by binding paths.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CopyOnWriteArrayList

class JsonStore(initial: Map<String, JsonElement> = emptyMap()) {
    fun interface Listener {
        /** `changedPath` is null when several keys changed at once. */
        fun onChange(changedPath: JsonPath?)
    }

    @Volatile
    private var root: JsonObject = JsonObject(initial)
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val lock = Any()

    // MARK: - Reading

    val snapshot: JsonObject get() = root

    val keys: List<String> get() = root.keys.sorted()

    fun has(key: String): Boolean = root.containsKey(key)

    fun get(path: JsonPath): JsonElement = root.valueAt(path)

    fun get(path: String): JsonElement = get(JsonPath.parse(path))

    operator fun get(path: String, default: JsonElement): JsonElement = get(path).takeUnless { it.isNull } ?: default

    // MARK: - Writing

    fun set(value: JsonElement, path: JsonPath) {
        synchronized(lock) {
            val old = root.valueAt(path)
            if (old == value && (!value.isNull || root.contains(path))) return
            root = root.withValue(value, path) as JsonObject
        }
        listeners.forEach { it.onChange(path) }
    }

    fun set(value: JsonElement, path: String) = set(value, JsonPath.parse(path))

    fun set(value: Any?, path: String) = set(jsonOf(value), JsonPath.parse(path))

    /** Merges `patch` into the top level of the state. */
    fun merge(patch: Map<String, JsonElement>) {
        var changed = false
        synchronized(lock) {
            val map = root.toMutableMap()
            for ((k, v) in patch) if (map[k] != v) { map[k] = v; changed = true }
            if (changed) root = JsonObject(map)
        }
        if (changed) listeners.forEach { it.onChange(null) }
    }

    fun replace(value: JsonElement) {
        synchronized(lock) { root = (value as? JsonObject) ?: JsonObject(emptyMap()) }
        listeners.forEach { it.onChange(null) }
    }

    // MARK: - Observation

    fun addListener(listener: Listener): Listener { listeners.add(listener); return listener }

    fun removeListener(listener: Listener) { listeners.remove(listener) }
}
