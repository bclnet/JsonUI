package com.bclnet.jsonui

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonDocumentTest {
    @Test
    fun parsesHeaderAndRoot() {
        val doc = JsonDocument.parse("""{"_ui":{"version":1,"state":{"a":1},"script":"function f(){}","strings":{"k":"v"}},"type":":VStack","content":[{"type":"Text","text":"hi"}]}""")
        assertEquals(1, doc.header.version)
        assertEquals(mapOf("a" to JsonPrimitive(1)), doc.header.state)
        assertEquals("function f(){}", doc.header.script)
        assertEquals(mapOf("k" to "v"), doc.header.strings)
        assertEquals("VStack", doc.root.type)
        assertEquals(JsonNode.Kind.VStack, doc.root.kind)
        assertEquals(1, doc.root.content.size)
        assertEquals("hi", doc.root.content[0]["text"].stringValue)
        assertEquals(doc, JsonDocument.parse(doc.toJsonString()))
        // kotlinx.serialization integration
        assertEquals(doc, JsonUIJson.decodeFromString(JsonDocument.serializer(), JsonUIJson.encodeToString(JsonDocument.serializer(), doc)))
    }

    @Test
    fun bareNodeAndErrors() {
        val doc = JsonDocument.parse("""{"type":"Text","text":"x"}""")
        assertTrue(doc.header.state.isEmpty())
        assertThrows(JsonDocumentException::class.java) { JsonDocument.parse("[1]") }
        assertThrows(JsonDocumentException::class.java) { JsonDocument.parse("""{"text":"x"}""") }
        assertThrows(JsonDocumentException::class.java) { JsonDocument.parse("""{"_ui":{"version":99},"type":"Text"}""") }
    }

    @Test
    fun normalizesTypeNames() {
        assertEquals("Text", JsonNode.normalize(":Text"))
        assertEquals("TextField", JsonNode.normalize("SwiftUI.TextField<Text>"))
        assertEquals("MyWidget", JsonNode.normalize("MyWidget"))
    }

    @Test
    fun samplesParseAndRender() {
        for (name in listOf("login.json", "profile.json", "survey.json")) {
            val doc = Samples.load(name)
            assertEquals(name, 1, doc.header.version)
            val runtime = JsonRuntime(doc)
            assertTrue(name, runtime.scriptErrors.isEmpty())
            var count = 0
            fun walk(node: JsonNode) {
                count++
                assertNotNull("unknown type ${node.type} in $name", node.kind)
                node.content.forEach(::walk)
                node.nodes("else").forEach(::walk)
            }
            walk(doc.root)
            assertTrue(name, count > 3)
            assertEquals(name, doc, JsonDocument.parse(doc.toJsonString()))
        }
    }

    @Test
    fun loginSampleState() {
        val doc = Samples.load("login.json")
        val ctx = JsonRuntime(doc).context
        val form = doc.root.content[0]
        assertEquals(JsonNode.Kind.Form, form.kind)
        val email = form.content[0].content[0]
        assertEquals(JsonNode.Kind.TextField, email.kind)
        assertEquals(JsonPath.parse("email"), ctx.bindingPath(email, "text"))
        assertEquals("Email", ctx.string(email, "title"))
        ctx.set(JsonPrimitive("me@example.com"), JsonPath.parse("email"))
        assertEquals("me@example.com", ctx.resolve(email, "text").stringValue)
    }

    @Test
    fun builderDsl() {
        val doc = jsonDocument(state = mapOf("email" to "", "agree" to false)) {
            form {
                section("Account") {
                    textField("Email", text = "\$email").keyboard("email").id("email")
                    toggle("I agree", isOn = "\$agree")
                }
                section {
                    button("Submit", host = "submit", args = mapOf("email" to "\$email")).disabled("\${!state.agree}").frame(maxWidth = "infinity")
                    ifThen("\$agree", { text("Thanks").font("caption").padding(8.0) }) { spacer() }
                }
            }
        }
        val json = doc.toJsonString()
        val parsed = JsonDocument.parse(json)
        assertEquals(doc, parsed)
        assertEquals(JsonNode.Kind.Form, parsed.root.kind)
        val sections = parsed.root.content
        assertEquals(2, sections.size)
        assertEquals("Account", sections[0]["header"].stringValue)
        assertEquals("email", sections[0].content[0]["keyboard"].stringValue)
        assertEquals("email", sections[0].content[0].id)
        val button = sections[1].content[0]
        assertEquals(JsonAction.Host("submit", mapOf("email" to JsonPrimitive("\$email"))), JsonAction.of(button["action"]))
        assertEquals("infinity", button["frame"]["maxWidth"].stringValue)
        val cond = sections[1].content[1]
        assertEquals(JsonNode.Kind.If, cond.kind)
        assertEquals(8.0, cond.content[0]["padding"].doubleValue)
        assertEquals(JsonNode.Kind.Spacer, cond.nodes("else")[0].kind)
        assertTrue(json.contains("\"_ui\""))
    }

    @Test
    fun swiftAndKotlinSerializationMatch() {
        // Both platforms serialize with sorted keys and compact numbers, so this
        // fixed text must round-trip byte for byte.
        val text = """{"_ui":{"state":{"a":1,"b":true},"version":1},"content":[{"text":"x","type":"Text"}],"type":"VStack"}"""
        assertEquals(text, JsonDocument.parse(text).toJsonString(pretty = false))
    }
}
