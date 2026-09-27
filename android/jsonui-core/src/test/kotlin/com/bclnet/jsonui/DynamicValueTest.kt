package com.bclnet.jsonui

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicValueTest {
    @Test
    fun parsing() {
        assertEquals(DynamicValue.Binding(JsonPath.parse("email")), DynamicValue.of("\$email"))
        assertEquals(DynamicValue.Binding(JsonPath.parse("phone.label")), DynamicValue.of("\$phone.label"))
        assertEquals(DynamicValue.Binding(JsonPath.parse("a.b")), DynamicValue.of(jsonObjectOf("\$bind" to "a.b")))
        assertEquals(DynamicValue.Expression("state.x > 1"), DynamicValue.of("\${state.x > 1}"))
        assertEquals(DynamicValue.Expression("1 + 1"), DynamicValue.of(jsonObjectOf("\$expr" to "1 + 1")))
        assertEquals(DynamicValue.Localized("title"), DynamicValue.of("@title"))
        assertEquals(DynamicValue.Literal(JsonPrimitive("\$literal")), DynamicValue.of("\$\$literal"))
        assertEquals(DynamicValue.Literal(JsonPrimitive("@literal")), DynamicValue.of("@@literal"))
        assertEquals(DynamicValue.Literal(JsonPrimitive("plain")), DynamicValue.of("plain"))
        assertEquals(DynamicValue.Literal(JsonPrimitive("$")), DynamicValue.of("$"))
        assertEquals(DynamicValue.Literal(JsonPrimitive(3)), DynamicValue.of(JsonPrimitive(3)))
        assertEquals(
            DynamicValue.Template(listOf(DynamicValue.TemplatePart.Text("Hi "), DynamicValue.TemplatePart.Expression("state.name"), DynamicValue.TemplatePart.Text("!"))),
            DynamicValue.of("Hi \${state.name}!"))
        assertEquals(
            DynamicValue.Template(listOf(DynamicValue.TemplatePart.Expression("f({a:1})"), DynamicValue.TemplatePart.Text(" and "), DynamicValue.TemplatePart.Expression("b"))),
            DynamicValue.of("\${ f({a:1}) } and \${b}"))
        assertEquals(DynamicValue.Literal(JsonPrimitive("no \${ close")), DynamicValue.of("no \${ close"))
    }

    @Test
    fun valueRoundTrip() {
        for (s in listOf("\$email", "\${state.x}", "@key", "\$\$literal", "Hi \${a} \${b}", "plain")) {
            assertEquals(s, JsonPrimitive(s), DynamicValue.of(s).value)
        }
    }

    @Test
    fun actions() {
        assertEquals(JsonAction.Host("submit"), JsonAction.of(JsonPrimitive("submit")))
        assertEquals(JsonAction.Script("go()"), JsonAction.of(JsonPrimitive("js: go()")))
        assertEquals(JsonAction.Script("go()"), JsonAction.of(JsonPrimitive("JS:go()")))
        assertEquals(JsonAction.Script("a()"), JsonAction.of(jsonObjectOf("script" to "a()")))
        assertEquals(JsonAction.Host("save", mapOf("x" to JsonPrimitive("\$x"))), JsonAction.of(jsonObjectOf("name" to "save", "args" to mapOf("x" to "\$x"))))
        assertEquals(JsonAction.Set(mapOf("step" to JsonPrimitive(2))), JsonAction.of(jsonObjectOf("set" to mapOf("step" to 2))))
        assertEquals(JsonAction.Sequence(listOf(JsonAction.Host("a"), JsonAction.Script("b()"))), JsonAction.of(jsonArrayOf("a", "js: b()")))
        assertNull(JsonAction.of(JsonPrimitive("")))
        for (action in listOf(JsonAction.Host("a"), JsonAction.Host("a", mapOf("k" to JsonPrimitive(1))), JsonAction.Script("x()"), JsonAction.Set(mapOf("a" to JsonPrimitive(1))), JsonAction.Sequence(listOf(JsonAction.Script("y()"))))) {
            assertEquals(action, JsonAction.of(action.value))
        }
    }
}
