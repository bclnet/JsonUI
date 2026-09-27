package com.bclnet.jsonui.compose.reflect

import com.bclnet.jsonui.JsonAction
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.JsonRuntime
import com.bclnet.jsonui.boolValue
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.stringValue
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSemanticsMapperTest {
    private fun box(top: Float, left: Float = 0f, width: Float = 300f, height: Float = 40f) = ReflectedBounds(left, top, left + width, top + height)

    @Test
    fun mapsFormElementsAndWiresActions() {
        var clicked = 0
        var typed = ""
        var toggled = 0
        val root = ReflectedElement(
            bounds = box(0f, height = 300f),
            children = listOf(
                ReflectedElement(text = listOf("Sign in"), heading = true, bounds = box(0f)),
                ReflectedElement(editableText = "a@b.co", text = listOf("Email"), key = "email", bounds = box(50f), setText = { typed = it }),
                ReflectedElement(editableText = "", isPassword = true, testTag = "password", bounds = box(100f), setText = {}),
                ReflectedElement(role = "Switch", toggleState = true, text = listOf("Remember me"), bounds = box(150f), onClick = { toggled++ }),
                ReflectedElement(role = "Button", text = listOf("Submit"), actionName = "submit", bounds = box(200f), onClick = { clicked++ }),
                ReflectedElement(progress = ReflectedProgress(0.5f, 0f, 1f, 4), bounds = box(250f), setProgress = {}),
            ),
        )
        val reflection = JsonSemanticsMapper(JsonActions()).map(root)
        val doc = reflection.document
        assertEquals(JsonNode.Kind.VStack, doc.root.kind)
        val nodes = doc.root.content
        assertEquals(6, nodes.size)
        assertEquals(JsonNode.Kind.Text, nodes[0].kind)
        assertEquals("headline", nodes[0]["font"].stringValue)
        assertEquals(JsonNode.Kind.TextField, nodes[1].kind)
        assertEquals("\$email", nodes[1]["text"].stringValue)
        assertEquals("Email", nodes[1]["title"].stringValue)
        assertEquals(JsonNode.Kind.SecureField, nodes[2].kind)
        assertEquals("\$password", nodes[2]["text"].stringValue)
        assertEquals(JsonNode.Kind.Toggle, nodes[3].kind)
        assertEquals("Remember me", nodes[3]["label"].stringValue)
        assertEquals(JsonNode.Kind.Button, nodes[4].kind)
        assertEquals(JsonAction.Host("submit"), JsonAction.of(nodes[4]["action"]))
        assertEquals(JsonNode.Kind.Slider, nodes[5].kind)
        assertEquals(0.2, nodes[5]["step"].doubleValue!!, 1e-9)
        assertEquals("a@b.co", doc.header.state["email"]?.stringValue)
        assertEquals(true, doc.header.state["isOn1"]?.boolValue)
        assertEquals(0.5, doc.header.state["value1"]?.doubleValue)

        // The rendered copy drives the original through host actions.
        val runtime = JsonRuntime(doc, actions = reflection.actions)
        val ctx = runtime.context
        ctx.perform(nodes[4], "action")
        assertEquals(1, clicked)
        ctx.perform(nodes[3], "onChange")
        assertEquals(1, toggled)
        runtime.store.set(JsonPrimitive("x@y.z"), "email")
        ctx.perform(nodes[1], "onChange")
        assertEquals("x@y.z", typed)
        assertTrue(reflection.warnings.isEmpty())
    }

    @Test
    fun infersLayoutFromGeometry() {
        val mapper = JsonSemanticsMapper()
        val vertical = listOf(ReflectedElement(bounds = box(0f)), ReflectedElement(bounds = box(40f)))
        val horizontal = listOf(ReflectedElement(bounds = box(0f, left = 0f, width = 100f)), ReflectedElement(bounds = box(0f, left = 100f, width = 100f)))
        val overlay = listOf(ReflectedElement(bounds = box(0f)), ReflectedElement(bounds = box(10f, left = 10f)))
        assertEquals(JsonSemanticsMapper.Layout.Vertical, mapper.inferLayout(vertical))
        assertEquals(JsonSemanticsMapper.Layout.Horizontal, mapper.inferLayout(horizontal))
        assertEquals(JsonSemanticsMapper.Layout.Overlay, mapper.inferLayout(overlay))

        val row = ReflectedElement(bounds = box(0f), children = listOf(
            ReflectedElement(text = listOf("A"), bounds = box(0f, left = 0f, width = 100f)),
            ReflectedElement(text = listOf("B"), bounds = box(0f, left = 100f, width = 100f)),
        ))
        val doc = JsonSemanticsMapper().map(ReflectedElement(children = listOf(row))).document
        assertEquals(JsonNode.Kind.HStack, doc.root.kind)
        assertEquals(listOf("A", "B"), doc.root.content.map { it["text"].stringValue })
    }

    @Test
    fun groupsRadioButtonsIntoPicker() {
        var picked = ""
        val radios = listOf("Yes", "No").mapIndexed { i, label ->
            ReflectedElement(role = "RadioButton", selected = i == 1, text = listOf(label), bounds = box(i * 40f), onClick = { picked = label })
        }
        val reflection = JsonSemanticsMapper().map(ReflectedElement(children = radios))
        val picker = reflection.document.root
        assertEquals(JsonNode.Kind.Picker, picker.kind)
        assertEquals("No", reflection.document.header.state["selection1"]?.stringValue)
        val runtime = JsonRuntime(reflection.document, actions = reflection.actions)
        runtime.store.set(JsonPrimitive("Yes"), "selection1")
        runtime.context.perform(picker, "onChange")
        assertEquals("Yes", picked)
    }

    @Test
    fun uniqueKeysAndDisabled() {
        val root = ReflectedElement(children = listOf(
            ReflectedElement(editableText = "", testTag = "name", bounds = box(0f), setText = {}),
            ReflectedElement(editableText = "", testTag = "name", bounds = box(40f), setText = {}, disabled = true),
        ))
        val doc = JsonSemanticsMapper().map(root).document
        assertEquals(listOf("\$name", "\$name2"), doc.root.content.map { it["text"].stringValue })
        assertEquals(true, doc.root.content[1]["disabled"].boolValue)
        assertNotNull(doc.header.state["name2"])
    }
}
