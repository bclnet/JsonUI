package com.bclnet.jsonui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URI

class JsonFragmentsTest {
    private val base = URI("https://example.com/forms/login.json")

    @Test fun referenceParsing() {
        val same = JsonFragmentReference.parse("#/a/b", base)
        assertNull(same.url)
        assertEquals("/a/b", same.pointer)
        assertEquals("/_ui/fragments/header", JsonFragmentReference.parse("#header", base).pointer)
        val relative = JsonFragmentReference.parse("shared.json#/x", base)
        assertEquals("https://example.com/forms/shared.json", relative.url.toString())
        assertEquals("/x", relative.pointer)
        assertEquals("https://cdn/x.json", JsonFragmentReference.parse("https://cdn/x.json", base).url.toString())
        assertEquals("", JsonFragmentReference.parse("https://cdn/x.json", base).pointer)
    }

    @Test fun pointer() {
        val v = jsonObjectOf("a" to jsonObjectOf("b" to jsonArrayOf(1, 2, jsonObjectOf("c" to "d"))), "e/f" to 5, "g~h" to 6)
        assertEquals(JsonPrimitive("d"), JsonFragments.valueAt("/a/b/2/c", v))
        assertEquals(jsonOf(5), JsonFragments.valueAt("/e~1f", v))
        assertEquals(jsonOf(6), JsonFragments.valueAt("/g~0h", v))
        assertEquals(v, JsonFragments.valueAt("", v))
        assertNull(JsonFragments.valueAt("/a/b/9", v))
        assertNull(JsonFragments.valueAt("a", v))
    }

    @Test fun localFragmentsWithOverridesAndSplicing() {
        val document = parseJson("""
        { "_ui": { "fragments": {
            "email": { "type": "TextField", "title": "Email", "text": "${'$'}email" },
            "buttons": [ { "type": "Button", "title": "OK" }, { "type": "Button", "title": "Cancel" } ] } },
          "type": "Form",
          "content": [
            { "${'$'}ref": "#email" },
            { "${'$'}ref": "#email", "title": "Work email", "text": "${'$'}work", "id": null },
            { "${'$'}ref": "#buttons" },
            { "${'$'}ref": "#/_ui/fragments/email/title" } ] }
        """)
        val resolved = JsonFragments().resolve(document, base)
        val content = resolved["content"] as JsonArray
        assertEquals(5, content.size)
        assertEquals(JsonPrimitive("Email"), content[0]["title"])
        assertEquals(JsonPrimitive("Work email"), content[1]["title"])
        assertEquals(JsonPrimitive("\$work"), content[1]["text"])
        assertEquals(JsonPrimitive("TextField"), content[1]["type"])
        assertEquals(JsonPrimitive("OK"), content[2]["title"])
        assertEquals(JsonPrimitive("Cancel"), content[3]["title"])
        assertEquals(JsonPrimitive("Email"), content[4])
        assertTrue(document.hasFragmentReferences)
        assertFalse(resolved.hasFragmentReferences)
        assertEquals("the bare string is not a node", 4, JsonDocument.fromValue(document, base, JsonFragments()).root.content.size)
    }

    @Test fun remoteFragmentsLoadedAndNested() {
        val shared = parseJson("""{ "sections": { "address": { "type": "Section", "header": "Address", "content": { "${'$'}ref": "fields.json#/street" } } } }""")
        val fields = parseJson("""{ "street": { "type": "TextField", "title": "Street" } }""")
        val loads = mutableListOf<String>()
        val resolver = JsonFragments { url ->
            loads += url.toString()
            when (url.path.substringAfterLast('/')) {
                "shared.json" -> shared
                "fields.json" -> fields
                else -> throw JsonFragmentException.MissingDocument(url.toString())
            }
        }
        val document = parseJson("""{ "type": "Form", "content": [ { "${'$'}ref": "../shared.json#/sections/address" } ] }""")
        val resolved = resolver.resolve(document, base)
        assertEquals(JsonPrimitive("Street"), resolved["content"][0]["content"]["title"])
        assertEquals(listOf("https://example.com/shared.json", "https://example.com/fields.json"), loads)
        val manual = JsonFragments()
        assertEquals(listOf("https://example.com/shared.json"), manual.externalReferences(document, base).map { it.toString() })
        manual.register(shared, URI("https://example.com/shared.json#/ignored"))
        assertEquals(listOf("https://example.com/fields.json"), manual.externalReferences(document, base).map { it.toString() })
        try { manual.resolve(document, base); fail("expected a missing document") } catch (e: JsonFragmentException.MissingDocument) { assertEquals("https://example.com/fields.json", e.url) }
        manual.register(fields, URI("https://example.com/fields.json"))
        assertEquals(emptyList<URI>(), manual.externalReferences(document, base))
        assertEquals(resolved, manual.resolve(document, base))
    }

    @Test fun errors() {
        val cyclic = parseJson("""{ "_ui": { "fragments": { "a": { "${'$'}ref": "#b" }, "b": { "${'$'}ref": "#a" } } }, "type": "Group", "content": { "${'$'}ref": "#a" } }""")
        try { JsonFragments().resolve(cyclic, base); fail() } catch (e: JsonFragmentException.Cycle) { assertTrue(e.ref in listOf("#a", "#b")) }
        val missing = parseJson("""{ "type": "Group", "content": { "${'$'}ref": "#nope" } }""")
        try { JsonFragments().resolve(missing, base); fail() } catch (e: JsonFragmentException.MissingFragment) { assertEquals("#nope", e.ref) }
    }

    @Test fun samples() {
        val file = File(Samples.directory, "fragments-login.json")
        val resolver = JsonFragments { url -> parseJson(File(url).readText()) }
        val document = JsonDocument.fromValue(parseJson(file.readText()), file.toURI(), resolver)
        assertEquals("Form", document.root.type)
        val titles = document.root.content.flatMap { node -> if (node.content.isEmpty()) listOf(node) else node.content }.mapNotNull { it["title"].text }
        assertTrue(titles.contains("Email"))
        assertTrue(titles.contains("Sign in"))
    }

    @Test fun strictAccessors() {
        assertEquals("a", JsonPrimitive("a").text)
        assertNull(JsonPrimitive(1).text)
        assertNull(jsonObjectOf().text)
        assertEquals(2, JsonPrimitive(2).integerValue)
        assertNull(JsonPrimitive(2.5).integerValue)
        assertNull(JsonPrimitive("2").numberValue)
        assertEquals(true, JsonPrimitive(true).flag)
        assertNull(JsonPrimitive("true").flag)
    }
}
