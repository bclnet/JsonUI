/*
 * InputViews.kt
 * JsonUI
 *
 * TextField, SecureField, TextEditor, Toggle, Picker, DatePicker, Slider,
 * Stepper and Button. Input views write into the form state at the node's
 * bound path.
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonAction
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.JsonPath
import com.bclnet.jsonui.arrayValue
import com.bclnet.jsonui.boolValue
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.jsonEquals
import com.bclnet.jsonui.jsonNumber
import com.bclnet.jsonui.stringValue
import com.bclnet.jsonui.toJsonString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A two-way binding between a node property and the form state. */
class JsonBinding(private val context: JsonContext, private val node: JsonNode, private val key: String, private val onChange: JsonAction?) {
    val path: JsonPath? = context.bindingPath(node, key)
    val isBound: Boolean get() = path != null

    val value: JsonElement get() = path?.let { context.get(it) } ?: context.resolve(node, key)

    fun set(newValue: JsonElement) {
        val path = path ?: return
        if (context.get(path) == newValue) return
        context.set(newValue, path)
        onChange?.let { context.perform(it) }
    }

    fun setString(s: String) = set(JsonPrimitive(s))
    fun setBool(b: Boolean) = set(JsonPrimitive(b))
    fun setDouble(d: Double) = set(jsonNumber(d))
}

@Composable
fun rememberJsonBinding(node: JsonNode, key: String, context: JsonContext, onChangeKey: String = "onChange"): JsonBinding =
    remember(node, key, context) { JsonBinding(context, node, key, context.action(node, onChangeKey)) }

@OptIn(ExperimentalMaterial3Api::class)
object JsonInputViews {
    @Composable
    private fun enabled(node: JsonNode, context: JsonContext): Boolean = !LocalJsonDisabled.current && !context.isDisabled(node)

    // MARK: - Text input

    @Composable
    fun TextField(node: JsonNode, context: JsonContext, modifier: Modifier, secure: Boolean) {
        val binding = rememberJsonBinding(node, "text", context)
        val title = context.string(node, "title") ?: context.string(node, "placeholder")
        val error = context.string(node, "error")?.takeIf { it.isNotEmpty() }
        val keyboard = node["keyboard"].stringValue
        OutlinedTextField(
            value = binding.value.stringValue ?: "",
            onValueChange = { binding.setString(it) },
            modifier = modifier.fillMaxWidth(),
            enabled = enabled(node, context),
            label = title?.let { { Text(it) } },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secure) androidx.compose.ui.text.input.KeyboardType.Password else JsonStyles.keyboardType(keyboard),
                capitalization = if (secure) androidx.compose.ui.text.input.KeyboardCapitalization.None else JsonStyles.capitalization(node["autocapitalization"].stringValue, keyboard),
                autoCorrectEnabled = !(secure || keyboard == "email" || keyboard == "url"),
                imeAction = if (context.hasAction(node, "onCommit")) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { context.perform(node, "onCommit") }),
        )
    }

    @Composable
    fun TextEditor(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "text", context)
        val error = context.string(node, "error")?.takeIf { it.isNotEmpty() }
        OutlinedTextField(
            value = binding.value.stringValue ?: "",
            onValueChange = { binding.setString(it) },
            modifier = modifier.fillMaxWidth().heightIn(min = (node["minHeight"].doubleValue ?: 60.0).dp),
            enabled = enabled(node, context),
            label = context.string(node, "title")?.let { { Text(it) } },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
    }

    // MARK: - Toggle

    @Composable
    fun Toggle(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "isOn", context)
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            JsonLabel(node["label"], context, model, Modifier.weight(1f))
            Switch(checked = binding.value.boolValue ?: false, onCheckedChange = { binding.setBool(it) }, enabled = enabled(node, context))
        }
    }

    // MARK: - Picker

    class PickerOption(val value: JsonElement, val label: String) {
        val id: String get() = value.stringValue ?: value.toJsonString()
    }

    fun options(node: JsonNode, context: JsonContext): List<PickerOption> =
        (context.resolve(node, "options").arrayValue ?: emptyList()).map { option ->
            if (option is JsonObject) {
                val o: JsonElement = option
                val value = o["value"].takeUnless { it is kotlinx.serialization.json.JsonNull } ?: o["id"]
                PickerOption(value, o["label"].stringValue ?: o["title"].stringValue ?: value.stringValue ?: "")
            } else PickerOption(option, option.stringValue ?: "")
        }

    @Composable
    fun Picker(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "selection", context)
        val options = options(node, context)
        val current = binding.value
        val selected = options.firstOrNull { jsonEquals(it.value, current) }
        val enabled = enabled(node, context)
        val label = node["label"]
        when (context.string(node, "style")) {
            "segmented" -> Column(modifier.fillMaxWidth()) {
                if (label.stringValue != null || label is JsonObject) JsonLabel(label, context, model)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option === selected,
                            onClick = { binding.set(option.value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            enabled = enabled,
                        ) { Text(option.label) }
                    }
                }
            }
            "inline", "wheel" -> Column(modifier.fillMaxWidth()) {
                if (label.stringValue != null || label is JsonObject) JsonLabel(label, context, model)
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { binding.set(option.value) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option === selected, onClick = { binding.set(option.value) }, enabled = enabled)
                        Text(option.label)
                    }
                }
            }
            else -> {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selected?.label ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        label = label.stringValue?.let { { Text(context.resolve(label).stringValue ?: "") } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { binding.set(option.value); expanded = false })
                        }
                    }
                }
            }
        }
    }

    // MARK: - DatePicker

    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val isoDateTime: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    @Composable
    fun DatePicker(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "selection", context)
        val components = context.string(node, "components", "date")
        val label = context.string(node, "label") ?: ""
        val enabled = enabled(node, context)
        val text = binding.value.stringValue ?: ""
        var showDate by remember { mutableStateOf(false) }
        var showTime by remember { mutableStateOf(false) }
        val parsedDate = runCatching { LocalDate.parse(text.take(10), isoDate) }.getOrNull()
        val parsedTime = runCatching { if (components == "time") LocalTime.parse(text, isoTime) else Instant.parse(text).atOffset(ZoneOffset.UTC).toLocalTime() }.getOrNull()

        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            if (components != "time") {
                OutlinedButton(onClick = { showDate = true }, enabled = enabled) { Text(parsedDate?.toString() ?: "Select date") }
            }
            if (components != "date") {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showTime = true }, enabled = enabled) { Text(parsedTime?.format(isoTime) ?: "Select time") }
            }
        }

        if (showDate) {
            val state = rememberDatePickerState(initialSelectedDateMillis = parsedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                            binding.setString(if (components == "date") date.format(isoDate) else isoDateTime.format(date.atTime(parsedTime ?: LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC)))
                        }
                        showDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            ) { DatePicker(state = state) }
        }
        if (showTime) {
            val state = rememberTimePickerState(initialHour = parsedTime?.hour ?: 0, initialMinute = parsedTime?.minute ?: 0)
            AlertDialog(
                onDismissRequest = { showTime = false },
                confirmButton = {
                    TextButton(onClick = {
                        val time = LocalTime.of(state.hour, state.minute)
                        binding.setString(if (components == "time") time.format(isoTime) else isoDateTime.format((parsedDate ?: LocalDate.now()).atTime(time).toInstant(ZoneOffset.UTC)))
                        showTime = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
                text = { TimePicker(state = state) },
            )
        }
    }

    // MARK: - Slider / Stepper

    @Composable
    fun Slider(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "value", context)
        val min = context.double(node, "min", 0.0)
        val max = context.double(node, "max", 1.0)
        val step = node["step"].doubleValue?.takeIf { it > 0 }
        val label = context.string(node, "label")
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label != null) Text(label)
            Slider(
                value = (binding.value.doubleValue ?: min).toFloat(),
                onValueChange = { v -> binding.setDouble(if (step != null) (Math.round(v / step) * step) else v.toDouble()) },
                valueRange = min.toFloat()..max.toFloat(),
                steps = if (step != null) (((max - min) / step).roundToInt() - 1).coerceAtLeast(0) else 0,
                enabled = enabled(node, context),
                modifier = Modifier.weight(1f),
            )
        }
    }

    @Composable
    fun Stepper(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val binding = rememberJsonBinding(node, "value", context)
        val step = node["step"].doubleValue ?: 1.0
        val min = context.double(node, "min")
        val max = context.double(node, "max")
        val value = binding.value.doubleValue ?: 0.0
        val enabled = enabled(node, context)
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.string(node, "label") ?: "", Modifier.weight(1f))
            IconButton(onClick = { binding.setDouble(value - step) }, enabled = enabled && (min == null || value - step >= min)) { Icon(Icons.Filled.Remove, contentDescription = "Decrement") }
            IconButton(onClick = { binding.setDouble(value + step) }, enabled = enabled && (max == null || value + step <= max)) { Icon(Icons.Filled.Add, contentDescription = "Increment") }
        }
    }

    // MARK: - Button

    @Composable
    fun Button(node: JsonNode, context: JsonContext, model: JsonUIModel, modifier: Modifier) {
        val enabled = enabled(node, context)
        val destructive = node["role"].stringValue == "destructive"
        val onClick = { context.perform(node, "action") }
        val label: @Composable () -> Unit = { JsonLabel(node["label"], context, model) }
        val m = if (node["frame"]["maxWidth"].stringValue == "infinity") modifier.fillMaxWidth() else modifier
        when (context.string(node, "style")) {
            "borderedProminent" -> Button(onClick, m, enabled, colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) { label() }
            "bordered" -> OutlinedButton(onClick, m, enabled, colors = if (destructive) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors()) { label() }
            else -> TextButton(onClick, m, enabled, colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) { label() }
        }
    }
}
