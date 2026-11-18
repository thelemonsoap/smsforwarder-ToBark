package com.lanbing.smsforwarder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

// This holder is deliberately not observable state: Text supplies its metrics synchronously
// during measurement, and the parent consumes them in that same pass without recomposition.
private class FirstLineMetrics { var center: Float? = null }

@Composable
internal fun LogMessageLayout(message: String, time: String, markerTint: Color, modifier: Modifier = Modifier) {
    val firstLine = remember { FirstLineMetrics() }
    Layout(modifier = modifier, content = {
        Box(Modifier.size(8.dp).background(markerTint, CircleShape).testTag("log-status-dot"))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("log-message"),
            onTextLayout = { result ->
                firstLine.center = if (result.lineCount > 0)
                    (result.getLineTop(0) + result.getLineBottom(0)) / 2f else 0f
            })
        if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) { measurables, constraints ->
        val markerSize = 8.dp.roundToPx()
        val marker = measurables[0].measure(Constraints.fixed(markerSize, markerSize))
        val textStart = marker.width + 12.dp.roundToPx()
        val textConstraints = Constraints(maxWidth = (constraints.maxWidth - textStart).coerceAtLeast(0),
            maxHeight = constraints.maxHeight)
        // Measure text first so this pass uses actual font metrics, including fallback fonts,
        // first-line trimming and Android's non-linear large-font scaling.
        val body = measurables[1].measure(textConstraints)
        val timestamp = measurables.getOrNull(2)?.measure(textConstraints)
        val timestampTop = body.height + if (timestamp == null) 0 else 4.dp.roundToPx()
        val width = constraints.constrainWidth(textStart + max(body.width, timestamp?.width ?: 0))
        val height = constraints.constrainHeight(max(marker.height, timestampTop + (timestamp?.height ?: 0)))
        val markerTop = ((firstLine.center ?: (marker.height / 2f)) - marker.height / 2f).roundToInt().coerceAtLeast(0)
        layout(width, height) {
            marker.placeRelative(0, markerTop)
            body.placeRelative(textStart, 0)
            timestamp?.placeRelative(textStart, timestampTop)
        }
    }
}
