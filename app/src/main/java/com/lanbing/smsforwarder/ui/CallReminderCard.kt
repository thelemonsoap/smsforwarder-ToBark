package com.lanbing.smsforwarder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import com.lanbing.smsforwarder.utils.CallDispatcher
import com.lanbing.smsforwarder.utils.ForwardingServiceController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallReminderCard(channels: List<Channel>, permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var revision by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false)) }
    var missed by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_MISSED_CALL_REMINDER_ENABLED, false)) }
    var selectedId by remember { mutableStateOf(prefs.getString(Constants.PREF_CALL_REMINDER_CHANNEL_ID, null)) }
    var showChannels by remember { mutableStateOf(false) }
    var showNumberSettings by remember { mutableStateOf(false) }
    var showPhoneSettings by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf(Constants.PREF_CALL_LISTENER_STATUS, Constants.PREF_CALL_LAST_EVENT, Constants.PREF_CALL_LAST_EVENT_TIME)) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun refresh(diagnose: Boolean = false) {
        revision++
        ForwardingServiceController.ensureRunning(context, diagnose)
        context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE).setPackage(context.packageName))
    }
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showPhoneSettings = !granted
        refresh()
    }
    val numberPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showNumberSettings = !granted
        refresh()
    }
    LaunchedEffect(permissionUpdateTrigger) {
        enabled = prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false)
        missed = prefs.getBoolean(Constants.PREF_MISSED_CALL_REMINDER_ENABLED, false)
        selectedId = prefs.getString(Constants.PREF_CALL_REMINDER_CHANNEL_ID, null)
        refresh()
    }
    val hasPhone = remember(permissionUpdateTrigger, revision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    val hasNumber = remember(permissionUpdateTrigger, revision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("电话提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("来电时推送", Modifier.weight(1f))
                Switch(enabled, onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean(Constants.PREF_CALL_REMINDER_ENABLED, it).apply()
                    refresh()
                    if (it && !hasPhone) phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
                })
            }
            Text("开启首页转发服务后提醒 SIM 卡来电。来电提醒独立于短信关键词规则；不包含微信等网络电话。",
                style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("未接／拒接后再提醒一次", Modifier.weight(1f))
                    Switch(missed, onCheckedChange = {
                        missed = it
                        prefs.edit().putBoolean(Constants.PREF_MISSED_CALL_REMINDER_ENABLED, it).apply()
                        refresh()
                    })
                }
                Text("系统无法区分未接与拒接；已接听的来电不会触发此提醒。", style = MaterialTheme.typography.bodySmall)
                Text("电话状态权限：${if (hasPhone) "已授权" else "未授权，无法监听来电"}")
                if (!hasPhone) {
                    OutlinedButton(onClick = { phonePermission.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("授权电话权限") }
                }
                Text("来电号码权限：${if (hasNumber) "已授权" else "未授权，将显示未知号码"}")
                if (!hasNumber) {
                    OutlinedButton(onClick = { numberPermission.launch(Manifest.permission.READ_CALL_LOG) }) { Text("授权显示来电号码（可选）") }
                }
                Text("号码权限用于获取此次来电号码，不读取历史通话记录；部分系统即使授权也可能隐藏号码。", style = MaterialTheme.typography.bodySmall)
                Text("号码最多等待 3 秒，获得号码即推送；仍未知则先提醒。系统稍后补交号码时，最多再发一次‘来电号码补充’。", style = MaterialTheme.typography.bodySmall)
                val channelName = if (selectedId == null) "全部通道" else channels.find { it.id == selectedId }?.name ?: "原通道已删除，请重新选择"
                OutlinedButton(onClick = { showChannels = true }) { Text("推送通道：$channelName") }
                if (channels.isEmpty()) Text("请先在通道页添加 Webhook / Bark 通道。", color = MaterialTheme.colorScheme.error)
                val listenerStatus = remember(revision, permissionUpdateTrigger) {
                    prefs.getString(Constants.PREF_CALL_LISTENER_STATUS, "等待检查") ?: "等待检查"
                }
                val lastEvent = remember(revision, permissionUpdateTrigger) {
                    val event = prefs.getString(Constants.PREF_CALL_LAST_EVENT, null)
                    val time = prefs.getLong(Constants.PREF_CALL_LAST_EVENT_TIME, 0L)
                    if (event == null || time == 0L) "尚未检测到电话事件" else
                        "$event\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))}"
                }
                Text("最近监听状态：$listenerStatus", style = MaterialTheme.typography.bodySmall)
                Text("最近电话事件：$lastEvent", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { refresh(diagnose = true) }) { Text("重新检查监听") }
                OutlinedButton(enabled = !testing && channels.isNotEmpty(), onClick = {
                    testing = true
                    testResult = null
                    CallDispatcher.sendTest(context) {
                        testing = false
                        testResult = it
                    }
                }) { Text(if (testing) "正在发送测试…" else "发送测试提醒") }
                testResult?.let { Text("通道测试：$it", style = MaterialTheme.typography.bodySmall) }
                Text("测试只验证所选电话提醒通道。请再用另一部手机拨入；日志应出现响铃和电话提醒发送结果。应用通知中的短信／电话过滤不影响此功能。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showChannels) AlertDialog(
        onDismissRequest = { showChannels = false },
        title = { Text("电话提醒推送通道") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val options = listOf(null to "全部通道") + channels.map { it.id to it.name }
                options.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedId = id
                        prefs.edit().putString(Constants.PREF_CALL_REMINDER_CHANNEL_ID, id).apply()
                        refresh()
                        showChannels = false
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedId == id, onClick = null)
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showChannels = false }) { Text("关闭") } }
    )
    if (showPhoneSettings) AlertDialog(
        onDismissRequest = { showPhoneSettings = false },
        title = { Text("电话状态权限未开启") },
        text = { Text("监听来电需要电话状态权限。可到系统设置开启权限后返回；短信转发可继续使用。") },
        confirmButton = { TextButton(onClick = {
            showPhoneSettings = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("打开系统设置") } },
        dismissButton = { TextButton(onClick = { showPhoneSettings = false }) { Text("暂不开启") } }
    )
    if (showNumberSettings) AlertDialog(
        onDismissRequest = { showNumberSettings = false },
        title = { Text("来电号码权限未开启") },
        text = { Text("仍可推送未知号码来电。可到系统设置尝试开启通话记录权限；部分系统不允许此权限。") },
        confirmButton = { TextButton(onClick = {
            showNumberSettings = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("打开系统设置") } },
        dismissButton = { TextButton(onClick = { showNumberSettings = false }) { Text("继续使用未知号码") } }
    )
}
