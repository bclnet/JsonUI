/*
 * SemanticsReflector.kt
 * JsonUI
 *
 * Reads a composable's semantics tree (the public RootForTest / SemanticsOwner
 * API that accessibility and UI tests use) into ReflectedElements and maps
 * them to a JsonUI document. Use `Modifier.jsonKey("email")` on inputs and
 * `Modifier.jsonAction("save")` on buttons to name the reflected state keys
 * and host actions.
 */
package com.bclnet.jsonui.compose.reflect

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import com.bclnet.jsonui.JsonActions

/** Semantics property carrying the state key of an input. */
val JsonUIKeyProperty = SemanticsPropertyKey<String>("JsonUIKey")

/** Semantics property carrying the host action name of a clickable. */
val JsonUIActionProperty = SemanticsPropertyKey<String>("JsonUIAction")

var SemanticsPropertyReceiver.jsonUIKey: String by JsonUIKeyProperty
var SemanticsPropertyReceiver.jsonUIAction: String by JsonUIActionProperty

/** Names the state key the reflected input binds to. */
fun Modifier.jsonKey(key: String): Modifier = semantics { jsonUIKey = key }

/** Names the host action registered for the reflected click. */
fun Modifier.jsonAction(name: String): Modifier = semantics { jsonUIAction = name }

object SemanticsReflector {
    /** Snapshot of a semantics node and its subtree (merged tree). */
    fun element(node: SemanticsNode): ReflectedElement {
        val config = node.config
        val bounds = node.boundsInRoot
        val toggle = config.getOrNull(SemanticsProperties.ToggleableState)
        val range = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        val onClick = config.getOrNull(SemanticsActions.OnClick)?.action
        val setText = config.getOrNull(SemanticsActions.SetText)?.action
        val setProgress = config.getOrNull(SemanticsActions.SetProgress)?.action
        return ReflectedElement(
            role = config.getOrNull(SemanticsProperties.Role)?.toString(),
            text = config.getOrNull(SemanticsProperties.Text)?.map(AnnotatedString::text) ?: emptyList(),
            editableText = config.getOrNull(SemanticsProperties.EditableText)?.text,
            isPassword = config.contains(SemanticsProperties.Password),
            toggleState = when (toggle) { ToggleableState.On -> true; ToggleableState.Off -> false; else -> null },
            selected = config.getOrNull(SemanticsProperties.Selected),
            progress = range?.let { ReflectedProgress(it.current, it.range.start, it.range.endInclusive, it.steps) },
            contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull(),
            disabled = config.contains(SemanticsProperties.Disabled),
            heading = config.contains(SemanticsProperties.Heading),
            key = config.getOrNull(JsonUIKeyProperty),
            actionName = config.getOrNull(JsonUIActionProperty),
            testTag = config.getOrNull(SemanticsProperties.TestTag),
            bounds = ReflectedBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            children = node.children.map { element(it) },
            onClick = onClick?.let { action -> { action() } },
            setText = setText?.let { action -> { text: String -> action(AnnotatedString(text)) } },
            setProgress = setProgress?.let { action -> { value: Float -> action(value) } },
        )
    }

    /** The merged semantics root of the Compose view hosting `view`, if available. */
    fun root(view: View): SemanticsNode? = (view as? RootForTest)?.semanticsOwner?.rootSemanticsNode

    /** Finds the node tagged with `testTag` in the tree under `node`. */
    fun find(node: SemanticsNode, testTag: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.TestTag) == testTag) return node
        for (child in node.children) find(child, testTag)?.let { return it }
        return null
    }

    /** Reflects the subtree tagged `testTag` (or the whole tree) of the Compose view hosting `view`. */
    fun reflect(view: View, testTag: String? = null, actions: JsonActions = JsonActions()): JsonReflection? {
        val root = root(view) ?: return null
        val start = testTag?.let { find(root, it) } ?: root
        return JsonSemanticsMapper(actions).map(element(start))
    }
}
