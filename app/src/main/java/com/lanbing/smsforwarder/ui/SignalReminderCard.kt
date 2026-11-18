package com.lanbing.smsforwarder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.SmsForegroundService
import com.lanbing.smsforwarder.utils.ForwardingServiceController
import com.lanbing.smsforwarder.utils.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalReminderCard(channels: List<Channel>, permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var revision by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false)) }
    var initial by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_SIGNAL_SEND_INITIAL, true)) }
    var pauseCalls by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)) }
    var selected by remember { mutableStateOf(prefs.getString(Constants.PREF_SIGNAL_CHANNEL_ID, null)) }
    var showChannels by remember { mutableStateOf(false) }
    var showPermissionSettings by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf(Constants.PREF_SIGNAL_STATUS, Constants.PREF_SIGNAL_LAST_LABEL, Constants.PREF_SIGNAL_LAST_TIME)) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun refresh(check: Boolean = false) {
        revision++
        if (check) {
            val globalEnabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
            if (globalEnabled && prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)) {
                try { ContextCompat.startForegroundService(context, Intent(context, SmsForegroundService::class.java).setAction(SmsForegroundService.ACTION_CHECK_SIGNAL)) }
                catch (_: Exception) { prefs.edit().putString(Constants.PREF_SIGNAL_STATUS, "无法恢复服务，请检查通知权限及首页开关").apply() }
            } else prefs.edit().putString(Constants.PREF_SIGNAL_STATUS, "请先同意隐私政策并开启首页转发").apply()
        } else ForwardingServiceController.ensureRunning(context)
        context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE).setPackage(context.packageName))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showPermissionSettings = !granted
        refresh()
    }
    LaunchedEffect(permissionUpdateTrigger) {
        enabled = prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false)
        initial = prefs.getBoolean(Constants.PREF_SIGNAL_SEND_INITIAL, true)
        pauseCalls = prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)
        selected = prefs.getString(Constants.PREF_SIGNAL_CHANNEL_ID, null)
        refresh()
    }
    val hasPhone = remember(permissionUpdateTrigger, revision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    LiquidCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("信号提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LTE／5G 切换时推送", Modifier.weight(1f))
                Switch(enabled, onCheckedChange = { value ->
                    enabled = value
                    val editor = prefs.edit().putBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, value)
                    if (value) editor.remove(Constants.PREF_SIGNAL_LAST_LABEL).remove(Constants.PREF_SIGNAL_LAST_SUB_ID).remove(Constants.PREF_SIGNAL_LAST_TIME)
                    editor.apply()
                    refresh(check = value)
                    if (value && !hasPhone) permission.launch(Manifest.permission.READ_PHONE_STATE)
                })
            }
            Text("监测当前数据 SIM 卡的移动网络标识。首次变化后等待 5 分钟，再与变化前比较：相同不推送，不同只推送一次；期间来回波动不重新计时。LTE／LTE+ 归为 LTE；5G、5G+／5GA 归为 5G。", style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开启时提醒当前网络", Modifier.weight(1f))
                    Switch(initial, onCheckedChange = {
                        initial = it
                        prefs.edit().putBoolean(Constants.PREF_SIGNAL_SEND_INITIAL, it).apply()
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通话期间暂停信号提醒", Modifier.weight(1f))
                    Switch(pauseCalls, onCheckedChange = {
                        pauseCalls = it
                        prefs.edit().putBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, it).apply()
                        refresh()
                    })
                }
                Text("默认开启。从来电响铃或拨出开始，到所有 SIM 通话结束后 30 秒内静默更新网络状态，不补发信号变化。无需开启电话推送。", style = MaterialTheme.typography.bodySmall)
                Text("电话状态权限：${if (hasPhone) "已授权" else "未授权，无法识别移动网络"}")
                if (!hasPhone) OutlinedButton(onClick = { permission.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("授权电话权限") }
                val name = if (selected == null) "全部通道" else channels.find { it.id == selected }?.name ?: "原通道已删除，请重新选择"
                OutlinedButton(onClick = { showChannels = true }) { Text("推送通道：$name") }
                if (channels.isEmpty()) Text("请先添加 Bark / Webhook 通道。", color = MaterialTheme.colorScheme.error)
                val status = remember(revision, permissionUpdateTrigger) { prefs.getString(Constants.PREF_SIGNAL_STATUS, "等待监听") }
                val label = remember(revision, permissionUpdateTrigger) {
                    val network = prefs.getString(Constants.PREF_SIGNAL_LAST_LABEL, null)
                    val time = prefs.getLong(Constants.PREF_SIGNAL_LAST_TIME, 0L)
                    if (network == null || time == 0L) "等待稳定的 LTE 或 5G" else
                        "$network · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))}"
                }
                Text("监听状态：$status", style = MaterialTheme.typography.bodySmall)
                Text("最近确认的移动网络：$label", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { refresh(check = true) }) { Text("重新检查信号") }
            }
            Text("5G 按系统网络标识判断，NSA 的底层可能仍为 LTE。连接 Wi-Fi 时自动注销 LTE／5G 信号监听并暂停信号推送；断开后先静默确认5分钟建立当前基准，不补发期间变化，再恢复原5分钟过滤。Wi-Fi 开关仅开启但未连接时仍检测。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showChannels) AlertDialog(
        onDismissRequest = { showChannels = false }, title = { Text("信号提醒推送通道") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                (listOf(null to "全部通道") + channels.map { it.id to it.name }).forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = id
                        prefs.edit().putString(Constants.PREF_SIGNAL_CHANNEL_ID, id).apply()
                        showChannels = false
                        refresh()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == id, onClick = null); Text(name)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showChannels = false }) { Text("关闭") } }
    )
    if (showPermissionSettings) AlertDialog(
        onDismissRequest = { showPermissionSettings = false }, title = { Text("电话状态权限未开启") },
        text = { Text("识别移动网络需要电话状态权限，可以到系统设置开启后返回。无需读取通话记录或位置。") },
        confirmButton = { TextButton(onClick = { showPermissionSettings = false; PermissionUtils.openAppSettings(context) }) { Text("打开系统设置") } },
        dismissButton = { TextButton(onClick = { showPermissionSettings = false }) { Text("暂不开启") } })
}
