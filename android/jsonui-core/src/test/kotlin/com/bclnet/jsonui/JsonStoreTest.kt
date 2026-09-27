package com.bclnet.jsonui

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonStoreTest {
    @Test
    fun setGetAndListeners() {
        val store = JsonStore(mapOf("name" to JsonPrimitive("a")))
        val changes = mutableListOf<String>()
        store.addListener { changes.add(it?.toString() ?: "*") }
        store.set("b", "name")
        store.set("b", "name") // unchanged, no notification
        store.set("x", "address.city")
        store.merge(mapOf("flag" to JsonPrimitive(true)))
        assertEquals("b", store.get("name").stringValue)
        assertEquals("x", store.get("address.city").stringValue)
        assertEquals(true, store.get("flag").boolValue)
        assertEquals(listOf("name", "address.city", "*"), changes)
        assertEquals(listOf("address", "flag", "name"), store.keys)
    }

    @Test
    fun removeListener() {
        val store = JsonStore()
        var count = 0
        val listener = store.addListener { count++ }
        store.set(1, "a")
        store.removeListener(listener)
        store.set(2, "a")
        assertEquals(1, count)
    }
}
