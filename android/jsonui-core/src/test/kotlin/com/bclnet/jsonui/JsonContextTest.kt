package com.bclnet.jsonui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonContextTest {
    @Test
    fun resolvesBindingsTemplatesAndStrings() {
        val doc = JsonDocument(JsonUIHeader(state = mapOf("name" to JsonPrimitive("Sky"), "count" to JsonPrimitive(2)), strings = mapOf("title" to "Hello")), JsonNode(JsonNode.Kind.Text))
        val ctx = JsonRuntime(doc).context
        assertEquals("Sky", ctx.resolve(JsonPrimitive("\$name")).stringValue)
        assertEquals("Hi Sky, 2", ctx.resolve(JsonPrimitive("Hi \${state.name}, \${state.count}")).stringValue)
        assertEquals("Hello", ctx.resolve(JsonPrimitive("@title")).stringValue)
        assertEquals("missing", ctx.resolve(JsonPrimitive("@missing")).stringValue)
        assertEquals(true, ctx.resolve(JsonPrimitive("\${state.count === 2}")).boolValue)
        assertEquals(false, ctx.resolve(JsonPrimitive("\${!state.name}")).boolValue)
        assertEquals(jsonArrayOf("Sky", 1), ctx.resolve(jsonArrayOf("\$name", 1)))
        val node = JsonNode(JsonNode.Kind.Text, mapOf("hidden" to JsonPrimitive("\${state.count === 3}"), "disabled" to JsonPrimitive(true)))
        assertTrue(ctx.isVisible(node))
        assertTrue(ctx.isDisabled(node))
        assertTrue(ctx.child(disabled = true).isDisabled(JsonNode(JsonNode.Kind.Text)))
    }

    @Test
    fun forEachScopeMapsBindingsAndLocals() {
        val runtime = JsonRuntime(state = mapOf("phones" to jsonArrayOf(mapOf("label" to "Mobile"), mapOf("label" to "Home"))))
        val ctx = runtime.context
        val items = ctx.get(JsonPath.parse("phones")).arrayValue!!
        val child = ctx.child(JsonScope("phone", "i", JsonPath.parse("phones[1]"), 1, items[1]))
        assertEquals(JsonPath.parse("phones[1].label"), child.bindingPath(JsonPrimitive("\$phone.label")))
        assertEquals("Home", child.resolve(JsonPrimitive("\$phone.label")).stringValue)
        assertEquals("Home", child.resolve(JsonPrimitive("\${phone.label}")).stringValue)
        assertEquals("#1", child.resolve(JsonPrimitive("#\${i}")).stringValue)
        child.set(JsonPrimitive("Work"), JsonPath.parse("phone.label"))
        assertEquals("Work", runtime.store.get("phones[1].label").stringValue)
        val readOnly = ctx.child(JsonScope("opt", "j", null, 0, jsonObjectOf("v" to 1)))
        assertEquals(1.0, readOnly.resolve(JsonPrimitive("\$opt.v")).doubleValue)
        assertNull(readOnly.bindingPath(JsonPrimitive("\$opt.v")))
    }

    @Test
    fun actions() {
        val actions = JsonActions()
        val received = mutableListOf<Pair<String, JsonElement>>()
        actions.register("save", JsonActionHandler { name, args, _ -> received.add(name to args); jsonObjectOf("ok" to true) })
        val runtime = JsonRuntime(actions = actions, state = mapOf("name" to JsonPrimitive("Sky"), "step" to JsonPrimitive(1)))
        val ctx = runtime.context
        ctx.perform(JsonAction.Host("save", mapOf("who" to JsonPrimitive("\$name"), "n" to JsonPrimitive(2))))
        assertEquals(1, received.size)
        assertEquals("save", received[0].first)
        assertEquals(jsonObjectOf("who" to "Sky", "n" to 2), received[0].second)
        ctx.perform(JsonAction.Set(mapOf("step" to JsonPrimitive(2), "copy" to JsonPrimitive("\$name"))))
        assertEquals(2.0, runtime.store.get("step").doubleValue)
        assertEquals("Sky", runtime.store.get("copy").stringValue)
        ctx.perform(JsonAction.Sequence(listOf(JsonAction.Set(mapOf("step" to JsonPrimitive(3))), JsonAction.Script("state.name = 'X'"))))
        assertEquals(3.0, runtime.store.get("step").doubleValue)
        assertEquals("X", runtime.store.get("name").stringValue)
        assertEquals("""{"ok":true}""", runtime.scriptInvoke("save", """{"a":1}"""))
        assertEquals(jsonObjectOf("a" to 1), received.last().second)
        ctx.perform(JsonNode(JsonNode.Kind.Button, mapOf("action" to jsonObjectOf("set" to mapOf("step" to 9)))), "action")
        assertEquals(9.0, runtime.store.get("step").doubleValue)
    }

    @Test
    fun scriptHostBridge() {
        val runtime = JsonRuntime(state = mapOf("a" to jsonObjectOf("b" to 1)))
        assertEquals("1", runtime.scriptGet("a.b"))
        assertEquals("null", runtime.scriptGet("missing"))
        runtime.scriptSet("a.c", "\"x\"")
        assertEquals("x", runtime.store.get("a.c").stringValue)
        assertTrue(runtime.scriptHas("a"))
        assertFalse(runtime.scriptHas("z"))
        assertEquals("""["a"]""", runtime.scriptKeys())
        runtime.scriptMerge("""{"z":true}""")
        assertEquals("""{"a":{"b":1,"c":"x"},"z":true}""", runtime.scriptSnapshot())
    }
}
