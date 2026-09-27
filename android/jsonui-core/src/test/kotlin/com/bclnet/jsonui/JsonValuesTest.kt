package com.bclnet.jsonui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonValuesTest {
    @Test
    fun parseAndSerializeRoundTrip() {
        val text = """{"a":[1,2.5,true,null,"x"],"b":{"c":"d"}}"""
        val value = parseJson(text)
        assertEquals(1.0, value["a"][0].doubleValue)
        assertTrue(value["a"][0].isNumber)
        assertEquals(2.5, value["a"][1].doubleValue)
        assertEquals(true, value["a"][2].boolValue)
        assertTrue(value["a"][2].isBoolean)
        assertTrue(value["a"][3].isNull)
        assertEquals("x", value["a"][4].stringValue)
        assertEquals("d", value["b"]["c"].stringValue)
        assertEquals(text, value.toJsonString())
        assertEquals(value, parseJson(value.toJsonString(pretty = true)))
    }

    @Test
    fun booleansAreNotNumbers() {
        val value = parseJson("""{"t":true,"one":1,"zero":0,"f":false}""")
        assertTrue(value["t"].isBoolean); assertFalse(value["t"].isNumber)
        assertTrue(value["one"].isNumber); assertFalse(value["one"].isBoolean)
        assertEquals("1", value["one"].stringValue)
        assertEquals("true", value["t"].stringValue)
    }

    @Test
    fun conversions() {
        assertEquals("3", JsonPrimitive(3).stringValue)
        assertEquals("3.25", JsonPrimitive(3.25).stringValue)
        assertEquals(42.0, JsonPrimitive("42").doubleValue)
        assertEquals(true, JsonPrimitive("yes").boolValue)
        assertFalse(JsonPrimitive("").isTruthy)
        assertTrue(jsonArrayOf().isTruthy)
        assertEquals(false, JsonNull.boolValue)
        assertNull(JsonNull.stringValue)
    }

    @Test
    fun paths() {
        var value: JsonElement = jsonObjectOf("phones" to listOf(mapOf("number" to "1")))
        assertEquals("1", value.valueAt(JsonPath.parse("phones[0].number")).stringValue)
        assertEquals("1", value.valueAt(JsonPath.parse("phones.0.number")).stringValue)
        assertTrue(value.valueAt(JsonPath.parse("phones[3].number")).isNull)
        value = value.withValue(JsonPrimitive("2"), JsonPath.parse("phones[1].number"))
        assertEquals("2", value["phones"][1]["number"].stringValue)
        value = value.withValue(JsonPrimitive("Home"), JsonPath.parse("address.kind"))
        assertEquals("Home", value["address"]["kind"].stringValue)
        assertEquals("a.b[2].c", JsonPath.parse("a.b[2].c").toString())
    }

    @Test
    fun escaping() {
        assertEquals("\"a\\\"b\\\\c\\n\"", JsonPrimitive("a\"b\\c\n").toJsonString())
    }
}
