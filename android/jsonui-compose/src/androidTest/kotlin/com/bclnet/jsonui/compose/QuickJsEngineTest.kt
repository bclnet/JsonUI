package com.bclnet.jsonui.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclnet.jsonui.JsonAction
import com.bclnet.jsonui.JsonActionHandler
import com.bclnet.jsonui.JsonActions
import com.bclnet.jsonui.JsonDocument
import com.bclnet.jsonui.JsonPath
import com.bclnet.jsonui.JsonScope
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.jsonObjectOf
import com.bclnet.jsonui.stringValue
import com.bclnet.jsonui.toJsonString
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test: QuickJS ships native libraries, so this runs on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class QuickJsEngineTest {
    @Test
    fun bridgesStateExpressionsAndActions() {
        val json = """{"_ui":{"state":{"email":"a@b.co","items":[{"n":1},{"n":2}]},"script":"function twice(x){return x*2;}"},"type":"Text","text":"${'$'}{twice(state.items.length)}"}"""
        val invoked = mutableListOf<Pair<String, JsonElement>>()
        val actions = JsonActions().register("tick", JsonActionHandler { name, args, _ -> invoked.add(name to args); jsonObjectOf("ok" to true) })
        JsonUIModel(JsonDocument.parse(json), QuickJsEngine(), actions).use { model ->
            val ctx = model.context
            assertTrue(model.runtime.scriptErrors.toString(), model.runtime.scriptErrors.isEmpty())
            assertEquals("4", ctx.resolve(model.document.root, "text").stringValue)
            assertEquals("a@b.co", ctx.evaluate("state.email").stringValue)
            assertEquals(3.0, ctx.evaluate("state.items[1].n + 1").doubleValue)
            ctx.perform(JsonAction.Script("state.email = 'x@y.z'; state.count = (state.count || 0) + 5; host.invoke('tick', {by: 5})"))
            assertEquals("x@y.z", model.store.get("email").stringValue)
            assertEquals(5.0, model.store.get("count").doubleValue)
            assertEquals(1, invoked.size)
            assertEquals("""{"by":5}""", invoked[0].second.toJsonString())
            assertEquals("""{"ok":true}""", ctx.evaluate("JSON.stringify(host.invoke('tick', 1))").stringValue)
            val scoped = ctx.child(JsonScope("item", "i", JsonPath.parse("items[1]"), 1, jsonObjectOf("n" to 2)))
            assertEquals(21.0, scoped.evaluate("item.n * 10 + i").doubleValue)
            // Errors are reported, not fatal.
            assertTrue(ctx.evaluate("undefinedFunction()").stringValue == null)
            assertFalse(model.runtime.scriptErrors.isEmpty())
        }
    }
}
