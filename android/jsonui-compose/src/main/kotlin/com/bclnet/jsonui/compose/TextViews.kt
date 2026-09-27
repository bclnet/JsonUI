/*
 * TextViews.kt
 * JsonUI
 *
 * Text, Label, Image, Link and ProgressView.
 */
package com.bclnet.jsonui.compose

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclnet.jsonui.JsonContext
import com.bclnet.jsonui.JsonNode
import com.bclnet.jsonui.doubleValue
import com.bclnet.jsonui.get
import com.bclnet.jsonui.intValue
import com.bclnet.jsonui.stringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object JsonTextViews {
    @Composable
    fun Text(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val lineLimit = node["lineLimit"].intValue
        Text(
            text = context.string(node, "text") ?: "",
            modifier = modifier,
            maxLines = lineLimit ?: Int.MAX_VALUE,
            overflow = if (lineLimit != null) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = JsonStyles.textAlign(node["multilineTextAlignment"].stringValue),
        )
    }

    @Composable
    fun Label(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val title = context.string(node, "title") ?: context.string(node, "text") ?: ""
        val systemImage = node["systemImage"].stringValue
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (systemImage != null) Icon(JsonStyles.icon(systemImage), contentDescription = null)
            Text(title)
        }
    }

    @Composable
    fun Image(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val fill = context.string(node, "contentMode") == "fill"
        val scale = if (fill) ContentScale.Crop else ContentScale.Fit
        var m = modifier
        node["width"].doubleValue?.let { m = m.width(it.dp) }
        node["height"].doubleValue?.let { m = m.height(it.dp) }
        val systemName = context.string(node, "systemName")
        val name = context.string(node, "name")
        val url = context.string(node, "url")
        when {
            systemName != null -> Icon(JsonStyles.icon(systemName), contentDescription = node["accessibilityLabel"].stringValue, modifier = if (node.has("width") || node.has("height")) m else m.size(24.dp))
            name != null -> {
                val ctx = LocalContext.current
                val id = remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
                if (id != 0) Image(painterResource(id), contentDescription = node["accessibilityLabel"].stringValue, modifier = m, contentScale = scale)
                else Icon(JsonStyles.icon("photo"), contentDescription = null, modifier = m)
            }
            url != null -> RemoteImage(url, m, scale)
            else -> Icon(JsonStyles.icon("photo"), contentDescription = null, modifier = m)
        }
    }

    /** A dependency-free image loader; hosts wanting caching can register a custom `Image` renderer. */
    @Composable
    private fun RemoteImage(url: String, modifier: Modifier, scale: ContentScale) {
        var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
        var failed by remember(url) { mutableStateOf(false) }
        LaunchedEffect(url) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
            if (loaded != null) bitmap = loaded else failed = true
        }
        val image = bitmap
        when {
            image != null -> Image(image, contentDescription = null, modifier = modifier, contentScale = scale)
            failed -> Icon(JsonStyles.icon("photo"), contentDescription = null, modifier = modifier)
            else -> CircularProgressIndicator(modifier.size(24.dp))
        }
    }

    @Composable
    fun Link(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val title = context.string(node, "title") ?: context.string(node, "text") ?: ""
        val url = context.string(node, "url")
        val ctx = LocalContext.current
        Text(
            title,
            modifier = if (url != null) modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } } else modifier,
            color = if (url != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            style = LocalTextStyle.current.copy(textDecoration = if (url != null) TextDecoration.Underline else null),
        )
    }

    @Composable
    fun Progress(node: JsonNode, context: JsonContext, modifier: Modifier) {
        val label = context.string(node, "label")
        val value = context.double(node, "value")
        if (value != null) {
            val total = context.double(node, "total", 1.0)
            androidx.compose.foundation.layout.Column(modifier) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { (value / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp))
                if (label != null) Text(label)
            }
        }
    }
}
