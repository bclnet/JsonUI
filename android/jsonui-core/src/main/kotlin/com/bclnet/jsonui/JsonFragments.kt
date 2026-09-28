/*
 * JsonFragments.kt
 * JsonUI
 *
 * Fragments: pieces of JSON reused by reference. `{ "$ref": "shared.json#/sections/address" }`
 * is replaced by the value the JSON pointer names in that document, and
 * `{ "$ref": "#header" }` by `_ui.fragments.header` of the same document.
 * Keys beside `$ref` override the fragment's keys. Resolution happens before
 * a document is parsed, so every consumer sees plain JSON.
 */
package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

sealed class JsonFragmentException(message: String) : IllegalArgumentException(message) {
    class MissingDocument(val url: String) : JsonFragmentException("fragment document not loaded: $url")
    class MissingFragment(val ref: String) : JsonFragmentException("fragment not found: $ref")
    class Cycle(val ref: String) : JsonFragmentException("fragment refers to itself: $ref")
    class TooDeep : JsonFragmentException("fragments nested too deeply")
}

/** A parsed `$ref`: the document it points into (`null` is the referring document) and a JSON pointer. */
data class JsonFragmentReference(val url: URI?, val pointer: String) {
    val key: String get() = (url?.toString() ?: "") + "#" + pointer

    companion object {
        const val REF_KEY = "\$ref"
        const val FRAGMENTS_POINTER = "/_ui/fragments/"

        /** Parses `"shared.json#/a/b"`, `"#/a/b"`, `"#name"` (→ `_ui.fragments.name`) or `"shared.json"`. */
        fun parse(text: String, base: URI?): JsonFragmentReference {
            val hash = text.indexOf('#')
            val location = if (hash >= 0) text.substring(0, hash) else text
            var fragment = if (hash >= 0) java.net.URLDecoder.decode(text.substring(hash + 1), "UTF-8") else ""
            if (fragment.isNotEmpty() && !fragment.startsWith("/")) fragment = FRAGMENTS_POINTER + fragment
            val url: URI? = when {
                location.isEmpty() -> null
                else -> runCatching { URI(location) }.getOrNull()?.let { if (it.scheme != null) it else base?.resolve(it) ?: it }
            }
            return JsonFragmentReference(url, fragment)
        }
    }
}

class JsonFragments(/** Loads a document on demand; without one every referenced document must be registered first. */ var loader: ((URI) -> JsonElement)? = null) {
    private val _documents = HashMap<URI, JsonElement>()
    val documents: Map<URI, JsonElement> get() = _documents
    var maxDepth = 32

    /** Makes a fetched document available under its URL (the fragment part is ignored). */
    fun register(value: JsonElement, url: URI) { _documents[documentUri(url)] = value }

    fun document(url: URI): JsonElement? = _documents[documentUri(url)]

    /** Replaces every `$ref` in `value` (a document at `base`) by the fragment it names. */
    fun resolve(value: JsonElement, base: URI? = null): JsonElement = resolve(value, value, base, emptyList(), 0)

    /**
     * The documents that `value` refers to and that are not loaded yet. Fetch them, register them and
     * call again until the list is empty; then `resolve` cannot fail on a missing document.
     */
    fun externalReferences(value: JsonElement, base: URI? = null): List<URI> {
        val found = ArrayList<URI>()
        val seen = HashSet<URI>()
        val visited = HashSet<URI>()
        fun walk(v: JsonElement, base: URI?) {
            when (v) {
                is JsonObject -> {
                    v[JsonFragmentReference.REF_KEY]?.text?.let { ref ->
                        JsonFragmentReference.parse(ref, base).url?.let { url ->
                            val doc = documentUri(url)
                            val loaded = _documents[doc]
                            if (loaded != null) { if (visited.add(doc)) walk(loaded, doc) }
                            else if (seen.add(doc)) found += doc
                        }
                    }
                    for (child in v.values) walk(child, base)
                }
                is JsonArray -> for (item in v) walk(item, base)
                else -> {}
            }
        }
        walk(value, base)
        return found
    }

    private fun resolve(value: JsonElement, root: JsonElement, base: URI?, stack: List<String>, depth: Int): JsonElement {
        if (depth > maxDepth) throw JsonFragmentException.TooDeep()
        return when (value) {
            is JsonObject -> {
                val ref = value[JsonFragmentReference.REF_KEY]?.text
                if (ref != null) expand(ref, value, root, base, stack, depth)
                else JsonObject(value.mapValues { resolve(it.value, root, base, stack, depth + 1) })
            }
            is JsonArray -> {
                val out = ArrayList<JsonElement>()
                for (item in value) {
                    val resolved = resolve(item, root, base, stack, depth + 1)
                    // A reference to a list of nodes is spliced into the surrounding list.
                    if (item is JsonObject && item.containsKey(JsonFragmentReference.REF_KEY) && resolved is JsonArray) out.addAll(resolved) else out += resolved
                }
                JsonArray(out)
            }
            else -> value
        }
    }

    private fun expand(ref: String, overrides: JsonObject, root: JsonElement, base: URI?, stack: List<String>, depth: Int): JsonElement {
        val reference = JsonFragmentReference.parse(ref, base)
        val key = if (reference.url == null) (base?.toString() ?: "") + "#" + reference.pointer else reference.key
        if (key in stack) throw JsonFragmentException.Cycle(ref)
        var targetRoot = root
        var targetBase = base
        reference.url?.let { url ->
            val doc = documentUri(url)
            targetRoot = _documents[doc] ?: loader?.invoke(doc)?.also { _documents[doc] = it } ?: throw JsonFragmentException.MissingDocument(doc.toString())
            targetBase = doc
        }
        val fragment = valueAt(reference.pointer, targetRoot) ?: throw JsonFragmentException.MissingFragment(ref)
        var resolved = resolve(fragment, targetRoot, targetBase, stack + key, depth + 1)
        val extra = overrides.filterKeys { it != JsonFragmentReference.REF_KEY }
        if (extra.isNotEmpty() && resolved is JsonObject) {
            val merged = LinkedHashMap<String, JsonElement>(resolved)
            for ((k, v) in extra) {
                val value = resolve(v, root, base, stack, depth + 1)
                if (value is JsonNull) merged.remove(k) else merged[k] = value
            }
            resolved = JsonObject(merged)
        }
        return resolved
    }

    companion object {
        fun documentUri(url: URI): URI = if (url.fragment == null) url else URI(url.scheme, url.schemeSpecificPart, null)

        /** RFC 6901 JSON pointer lookup (`""` is the whole value; `~1` and `~0` escape `/` and `~`). */
        fun valueAt(pointer: String, value: JsonElement): JsonElement? {
            if (pointer.isEmpty()) return value
            if (!pointer.startsWith("/")) return null
            var current = value
            for (raw in pointer.substring(1).split("/")) {
                val token = raw.replace("~1", "/").replace("~0", "~")
                current = when (current) {
                    is JsonObject -> current[token] ?: return null
                    is JsonArray -> token.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                    else -> return null
                }
            }
            return current
        }
    }
}

/** Whether this value contains any `$ref`. */
val JsonElement.hasFragmentReferences: Boolean
    get() = when (this) {
        is JsonObject -> this[JsonFragmentReference.REF_KEY]?.text != null || values.any { it.hasFragmentReferences }
        is JsonArray -> any { it.hasFragmentReferences }
        else -> false
    }

/** Parses a document after resolving its fragments (`base` is the document's own URL). */
fun JsonDocument.Companion.fromValue(value: JsonElement, base: URI?, fragments: JsonFragments): JsonDocument = fromValue(fragments.resolve(value, base))
