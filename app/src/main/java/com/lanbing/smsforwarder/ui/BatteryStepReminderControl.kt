package com.lanbing.smsforwarder.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.SmsForegroundService
import com.lanbing.smsforwarder.utils.ForwardingServiceController

@Composable
fun BatteryStepReminderControl(permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_BATTERY_STEP_ENABLED, false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Constants.PREF_BATTERY_STEP_ENABLED) enabled = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(permissionUpdateTrigger) { enabled = prefs.getBoolean(Constants.PREF_BATTERY_STEP_ENABLED, false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("连续电量提醒（每10%）", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(Constants.PREF_BATTERY_STEP_ENABLED, it)
                    .remove(Constants.PREF_BATTERY_STEP_PREVIOUS).remove(Constants.PREF_BATTERY_STEP_LAST_THRESHOLD)
                    .remove(Constants.PREF_BATTERY_STEP_LAST_DIRECTION).apply()
                ForwardingServiceController.ensureRunning(context)
                context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE).setPackage(context.packageName))
            })
        }
        Text("开启后以当前电量为起点。充电或使用过程中，电量到达或经过 10%、20%、30%、40%、50%、60%、70%、80%、90% 时提醒，同档位重复事件不重复发送。",
            style = MaterialTheme.typography.bodySmall)
        Text("沿用电量提醒的消息格式、设备信息和下方提醒通道。此开关独立于高／低电量提醒；两类提醒在同次变化触发时合并发送。请保持首页转发开启。",
            style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
}
