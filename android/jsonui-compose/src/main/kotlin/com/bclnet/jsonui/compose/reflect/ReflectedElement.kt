/*
 * ReflectedElement.kt
 * JsonUI
 *
 * A toolkit-independent snapshot of one node of a Compose semantics tree.
 * `SemanticsReflector` builds these from `SemanticsNode`s; `JsonSemanticsMapper`
 * turns them into a JsonUI document. Keeping the model free of Compose types
 * lets the mapping be unit tested on the JVM.
 */
package com.bclnet.jsonui.compose.reflect

data class ReflectedBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ReflectedProgress(val current: Float, val min: Float, val max: Float, val steps: Int)

data class ReflectedElement(
    /** `Button`, `Switch`, `Checkbox`, `RadioButton`, `Image`, `Tab`, `DropdownList` or null. */
    val role: String? = null,
    /** `Text` semantics, in order. */
    val text: List<String> = emptyList(),
    /** `EditableText` semantics of a text field. */
    val editableText: String? = null,
    val isPassword: Boolean = false,
    /** `ToggleableState`: true / false, or null when not toggleable. */
    val toggleState: Boolean? = null,
    val selected: Boolean? = null,
    val progress: ReflectedProgress? = null,
    val contentDescription: String? = null,
    val disabled: Boolean = false,
    val heading: Boolean = false,
    /** From `Modifier.jsonKey(...)`. */
    val key: String? = null,
    /** From `Modifier.jsonAction(...)`. */
    val actionName: String? = null,
    val testTag: String? = null,
    val bounds: ReflectedBounds = ReflectedBounds(0f, 0f, 0f, 0f),
    val children: List<ReflectedElement> = emptyList(),
    /** Invokes the original `onClick`. */
    val onClick: (() -> Unit)? = null,
    /** Invokes the original `SetText` action. */
    val setText: ((String) -> Unit)? = null,
    /** Invokes the original `SetProgress` action. */
    val setProgress: ((Float) -> Unit)? = null,
) {
    /** All text of this element and its descendants, in order. */
    val allText: List<String> get() = text + children.flatMap { it.allText }

    val label: String get() = allText.joinToString(" ").trim().ifEmpty { contentDescription ?: "" }
}
