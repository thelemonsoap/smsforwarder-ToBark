package com.lanbing.smsforwarder.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.widget.ServiceStatusWidget

@Composable
fun ServiceWidgetCard() {
    val context = LocalContext.current
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("桌面小组件", style = MaterialTheme.typography.titleMedium)
            Text("显示服务状态和最近一次成功发送的消息。仅发送成功时更新消息，服务启停时同步状态，不定时刷新。点击卡片打开应用；系统意外强杀时状态可能暂时保留。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                val manager = AppWidgetManager.getInstance(context)
                val supported = Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported
                val requested = if (supported) try {
                    manager.requestPinAppWidget(ComponentName(context, ServiceStatusWidget::class.java), null, null)
                } catch (_: Exception) { false } else false
                if (!requested) Toast.makeText(context, "请长按桌面空白处 → 小组件 → 短信转发助手 → 转发服务状态", Toast.LENGTH_LONG).show()
            }) { Text("添加到桌面") }
        }
    }
}
