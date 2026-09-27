/*
 * JsonPath.kt
 * JsonUI
 *
 * A path into the form state: `phones[0].number` → [Key("phones"), Index(0), Key("number")].
 */
package com.bclnet.jsonui

data class JsonPath(val segments: List<Segment>) {
    sealed class Segment {
        data class Key(val key: String) : Segment()
        data class Index(val index: Int) : Segment()
    }

    val isEmpty: Boolean get() = segments.isEmpty()
    val first: Segment? get() = segments.firstOrNull()

    fun appending(other: JsonPath): JsonPath = JsonPath(segments + other.segments)
    fun appending(index: Int): JsonPath = JsonPath(segments + Segment.Index(index))
    fun dropFirst(): JsonPath = JsonPath(segments.drop(1))

    override fun toString(): String = buildString {
        for (segment in segments) {
            when (segment) {
                is Segment.Key -> if (isEmpty()) append(segment.key) else append('.').append(segment.key)
                is Segment.Index -> append('[').append(segment.index).append(']')
            }
        }
    }

    companion object {
        /** Parses `a.b[2].c`. Numeric dotted segments (`a.2.c`) are treated as indices. */
        fun parse(text: String): JsonPath {
            val segments = mutableListOf<Segment>()
            val current = StringBuilder()
            var inBracket = false
            fun flush() {
                if (current.isEmpty()) return
                val token = current.toString()
                val index = if (inBracket || token.all { it.isDigit() }) token.toIntOrNull() else null
                segments.add(if (index != null) Segment.Index(index) else Segment.Key(token))
                current.clear()
            }
            for (ch in text) {
                when (ch) {
                    '.' -> if (!inBracket) flush() else current.append(ch)
                    '[' -> { flush(); inBracket = true }
                    ']' -> { flush(); inBracket = false }
                    else -> current.append(ch)
                }
            }
            flush()
            return JsonPath(segments)
        }

        val empty = JsonPath(emptyList())
    }
}
