package com.lanbing.smsforwarder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The title owns a full row. Status and 48dp actions wrap together on narrow/large-text screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogPanelHeader(
    count: Int,
    autoRefresh: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onAutoRefreshChange: ((Boolean) -> Unit)?
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("转发日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("log-title"))
                Text("$count 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(Modifier.fillMaxWidth().testTag("log-toolbar"),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = if (autoRefresh) Color(0xFF22C55E).copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(100.dp), modifier = Modifier.testTag("log-refresh-status")) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (autoRefresh) Icon(Icons.Outlined.Circle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(6.dp))
                    Text(if (autoRefresh) "实时刷新中" else "已暂停刷新", style = MaterialTheme.typography.bodySmall,
                        color = if (autoRefresh) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.testTag("log-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onAutoRefreshChange != null) IconButton(
                    onClick = { onAutoRefreshChange(!autoRefresh) },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (autoRefresh) Color(0xFF22C55E).copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant)
                        .semantics { stateDescription = if (autoRefresh) "已开启" else "已暂停" }
                ) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = "自动刷新", tint = if (autoRefresh) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = onClear, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = .1f))) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "清除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
