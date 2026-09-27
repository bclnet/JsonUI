/*
 * Styles.kt
 * JsonUI
 *
 * Parsing of colors, fonts, alignments, icons and keyboard options.
 */
package com.bclnet.jsonui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.stringValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonStyles {
    // MARK: - Colors

    @Composable
    fun color(value: JsonElement, context: JsonContext): Color? {
        val resolved = context.resolve(value)
        if (resolved is JsonObject) {
            val o: JsonElement = resolved
            val dark = isSystemInDarkTheme()
            val pick = if (dark) o["dark"].takeUnless { it.stringValue == null } ?: o["light"] else o["light"].takeUnless { it.stringValue == null } ?: o["dark"]
            return color(pick, context)
        }
        val text = resolved.stringValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.startsWith("#")) return hexColor(text)
        val scheme = MaterialTheme.colorScheme
        return when (text.lowercase()) {
            "primary" -> scheme.onSurface
            "secondary" -> scheme.onSurfaceVariant
            "accent", "accentcolor" -> scheme.primary
            "clear", "transparent" -> Color.Transparent
            "black" -> Color.Black
            "white" -> Color.White
            "gray", "grey" -> Color.Gray
            "red" -> Color(0xFFFF3B30)
            "green" -> Color(0xFF34C759)
            "blue" -> Color(0xFF007AFF)
            "orange" -> Color(0xFFFF9500)
            "yellow" -> Color(0xFFFFCC00)
            "pink" -> Color(0xFFFF2D55)
            "purple" -> Color(0xFFAF52DE)
            "mint" -> Color(0xFF00C7BE)
            "teal" -> Color(0xFF30B0C7)
            "cyan" -> Color(0xFF32ADE6)
            "indigo" -> Color(0xFF5856D6)
            "brown" -> Color(0xFFA2845E)
            else -> null
        }
    }

    fun hexColor(text: String): Color? {
        var hex = text.removePrefix("#")
        if (hex.length == 3 || hex.length == 4) hex = hex.map { "$it$it" }.joinToString("")
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 8) Color(((value shr 24) and 0xff).toInt(), ((value shr 16) and 0xff).toInt(), ((value shr 8) and 0xff).toInt(), (value and 0xff).toInt())
        else Color(((value shr 16) and 0xff).toInt(), ((value shr 8) and 0xff).toInt(), (value and 0xff).toInt())
    }

    // MARK: - Fonts

    @Composable
    fun textStyle(value: JsonElement, context: JsonContext): TextStyle? {
        val resolved = context.resolve(value)
        val typography = MaterialTheme.typography
        if (resolved is JsonObject) {
            val o: JsonElement = resolved
            val size = o["size"].doubleValue ?: 16.0
            return TextStyle(
                fontSize = size.sp,
                fontWeight = fontWeight(o["weight"].stringValue) ?: FontWeight.Normal,
                fontFamily = fontFamily(o["design"].stringValue),
            )
        }
        resolved.doubleValue?.let { return TextStyle(fontSize = it.sp) }
        return when (resolved.stringValue) {
            "largeTitle" -> typography.displaySmall
            "title" -> typography.headlineMedium
            "title2" -> typography.headlineSmall
            "title3" -> typography.titleLarge
            "headline" -> typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            "subheadline" -> typography.bodyMedium
            "body" -> typography.bodyLarge
            "callout" -> typography.bodyMedium
            "footnote" -> typography.bodySmall
            "caption" -> typography.labelMedium
            "caption2" -> typography.labelSmall
            null -> null
            else -> null
        }
    }

    fun fontWeight(name: String?): FontWeight? = when (name) {
        "ultraLight" -> FontWeight.ExtraLight
        "thin" -> FontWeight.Thin
        "light" -> FontWeight.Light
        "regular" -> FontWeight.Normal
        "medium" -> FontWeight.Medium
        "semibold" -> FontWeight.SemiBold
        "bold" -> FontWeight.Bold
        "heavy" -> FontWeight.ExtraBold
        "black" -> FontWeight.Black
        else -> null
    }

    fun fontFamily(name: String?): FontFamily? = when (name) {
        "monospaced" -> FontFamily.Monospace
        "rounded" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        else -> null
    }

    // MARK: - Alignment

    fun horizontalAlignment(name: String?): Alignment.Horizontal = when (name) {
        "leading" -> Alignment.Start
        "trailing" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    fun verticalAlignment(name: String?): Alignment.Vertical = when (name) {
        "top", "firstTextBaseline" -> Alignment.Top
        "bottom", "lastTextBaseline" -> Alignment.Bottom
        else -> Alignment.CenterVertically
    }

    fun alignment(name: String?): Alignment = when (name) {
        "leading" -> Alignment.CenterStart
        "trailing" -> Alignment.CenterEnd
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        "topLeading" -> Alignment.TopStart
        "topTrailing" -> Alignment.TopEnd
        "bottomLeading" -> Alignment.BottomStart
        "bottomTrailing" -> Alignment.BottomEnd
        else -> Alignment.Center
    }

    fun textAlign(name: String?): TextAlign? = when (name) {
        "leading" -> TextAlign.Start
        "trailing" -> TextAlign.End
        "center" -> TextAlign.Center
        else -> null
    }

    // MARK: - Keyboard

    fun keyboardType(name: String?): KeyboardType = when (name) {
        "email" -> KeyboardType.Email
        "number" -> KeyboardType.Number
        "decimal" -> KeyboardType.Decimal
        "phone" -> KeyboardType.Phone
        "url" -> KeyboardType.Uri
        else -> KeyboardType.Text
    }

    fun capitalization(name: String?, keyboard: String?): KeyboardCapitalization = when (name) {
        "none" -> KeyboardCapitalization.None
        "words" -> KeyboardCapitalization.Words
        "sentences" -> KeyboardCapitalization.Sentences
        "characters" -> KeyboardCapitalization.Characters
        else -> if (keyboard == "email" || keyboard == "url") KeyboardCapitalization.None else KeyboardCapitalization.Sentences
    }

    // MARK: - Icons (SF Symbol names mapped to Material icons)

    fun icon(systemName: String): ImageVector = when (systemName.substringBefore(".fill")) {
        "checkmark" -> Icons.Filled.Check
        "checkmark.circle" -> Icons.Filled.CheckCircle
        "xmark", "xmark.circle" -> Icons.Filled.Close
        "plus", "plus.circle" -> Icons.Filled.Add
        "minus", "minus.circle" -> Icons.Filled.Remove
        "trash" -> Icons.Filled.Delete
        "pencil" -> Icons.Filled.Edit
        "person", "person.circle" -> Icons.Filled.Person
        "envelope" -> Icons.Filled.Email
        "lock" -> Icons.Filled.Lock
        "phone" -> Icons.Filled.Phone
        "photo" -> Icons.Filled.Image
        "doc.text", "doc" -> Icons.Filled.Description
        "gear", "gearshape" -> Icons.Filled.Settings
        "star" -> Icons.Filled.Star
        "heart" -> Icons.Filled.Favorite
        "info.circle" -> Icons.Filled.Info
        "exclamationmark.triangle", "exclamationmark.circle" -> Icons.Filled.Warning
        "magnifyingglass" -> Icons.Filled.Search
        "house" -> Icons.Filled.Home
        "calendar" -> Icons.Filled.DateRange
        "clock" -> Icons.Filled.Schedule
        "link" -> Icons.Filled.Link
        "arrow.right" -> Icons.AutoMirrored.Filled.ArrowForward
        "arrow.left" -> Icons.AutoMirrored.Filled.ArrowBack
        "chevron.right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        "chevron.down" -> Icons.Filled.KeyboardArrowDown
        "chevron.up" -> Icons.Filled.KeyboardArrowUp
        "paperplane" -> Icons.AutoMirrored.Filled.Send
        "square.and.arrow.up" -> Icons.Filled.Share
        "cart" -> Icons.Filled.ShoppingCart
        "bell" -> Icons.Filled.Notifications
        "mappin", "location" -> Icons.Filled.LocationOn
        "line.3.horizontal", "line.horizontal.3" -> Icons.Filled.Menu
        "ellipsis" -> Icons.Filled.MoreVert
        "arrow.clockwise" -> Icons.Filled.Refresh
        else -> Icons.Filled.Info
    }
}
