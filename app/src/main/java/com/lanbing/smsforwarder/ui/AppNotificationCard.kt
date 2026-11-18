package com.lanbing.smsforwarder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.utils.NotificationAppChoice
import com.lanbing.smsforwarder.utils.NotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNotificationCard(channels: List<Channel>, permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var revision by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_ENABLED, false)) }
    var selected by remember { mutableStateOf(NotificationSettings.selectedPackages(prefs)) }
    var selectedChannel by remember { mutableStateOf(prefs.getString(Constants.PREF_APP_NOTIFICATION_CHANNEL_ID, null)) }
    var skipPhoneSms by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_PHONE_SMS, true)) }
    var skipOngoing by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_ONGOING, true)) }
    var showApps by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var catalogRevision by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<NotificationAppChoice>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val blockedPackages = remember(permissionUpdateTrigger, revision) { NotificationSettings.phoneAndSmsPackages(context) }
    val hasAccess = remember(permissionUpdateTrigger, revision) { NotificationSettings.isAccessGranted(context) }
    LaunchedEffect(permissionUpdateTrigger) {
        enabled = prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_ENABLED, false)
        selected = NotificationSettings.selectedPackages(prefs)
        selectedChannel = prefs.getString(Constants.PREF_APP_NOTIFICATION_CHANNEL_ID, null)
        skipPhoneSms = prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_PHONE_SMS, true)
        skipOngoing = prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_ONGOING, true)
        revision++
        NotificationSettings.requestConnection(context)
    }
    LaunchedEffect(showApps, catalogRevision, permissionUpdateTrigger) {
        if (showApps) {
            loadingApps = true
            apps = withContext(Dispatchers.IO) { NotificationSettings.availableApps(context, prefs) }
            loadingApps = false
        }
    }
    fun openAccess() {
        try {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            Toast.makeText(context, "请在系统设置中开启本应用的通知访问权限", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("应用通知转发", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("转发选定应用的通知", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean(Constants.PREF_APP_NOTIFICATION_ENABLED, it).apply()
                    revision++
                    if (it) NotificationSettings.requestConnection(context)
                })
            }
            Text("开启首页转发服务后，将选定应用通知的标题、正文和时间发送到指定通道。只读取系统通知栏提供的文字。",
                style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                Text("通知访问权限：${if (hasAccess) "已授权" else "未授权"}")
                OutlinedButton(onClick = { openAccess() }) { Text(if (hasAccess) "管理通知访问权限" else "授权通知访问") }
                Text("系统若提示受限设置，可在本应用的系统详情页尝试“允许受限设置”，然后返回授权。",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showApps = true }) { Text("选择应用（已选 ${selected.size} 个）") }
                if (selected.isEmpty()) Text("尚未选择应用，不会转发任何应用通知。", color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("跳过短信／电话应用")
                        Text("推荐开启：短信与 SIM 来电由原有功能推送。关闭并选中这些应用可能重复。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = skipPhoneSms, onCheckedChange = {
                        skipPhoneSms = it
                        prefs.edit().putBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_PHONE_SMS, it).apply()
                        revision++
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("跳过常驻通知")
                        Text("过滤音乐播放、下载进度、后台服务等常驻提示。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = skipOngoing, onCheckedChange = {
                        skipOngoing = it
                        prefs.edit().putBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_ONGOING, it).apply()
                    })
                }
                Text("本应用自身和通知分组汇总始终跳过；相同通知刷新自动去重，正文变化的新消息继续推送。",
                    style = MaterialTheme.typography.bodySmall)
                val channelName = if (selectedChannel == null) "全部通道" else channels.find { it.id == selectedChannel }?.name ?: "原通道已删除，请重新选择"
                OutlinedButton(onClick = { showChannels = true }) { Text("推送通道：$channelName") }
                if (channels.isEmpty()) Text("请先在通道页添加 Webhook / Bark 通道。", color = MaterialTheme.colorScheme.error)
                Text("应用未发布通知、通知没有文字或系统隐藏正文时，无法取得完整消息。Android 新版本可能隐藏验证码等敏感内容。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showApps) AlertDialog(
        onDismissRequest = { showApps = false },
        title = { Text("选择要转发通知的应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("搜索应用名称") }, singleLine = true)
                Text("应用列表包含可打开的应用及最近通知来源。短信／电话应用默认过滤。", style = MaterialTheme.typography.bodySmall)
                if (loadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
                val filtered = apps.filter { it.name.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
                if (filtered.isEmpty() && !loadingApps) Text("未找到应用；收到该应用通知后可刷新列表。")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        val blocked = skipPhoneSms && app.packageName in blockedPackages
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = checked, enabled = !blocked || checked, role = Role.Checkbox,
                                onValueChange = { choose ->
                                    selected = if (choose) selected + app.packageName else selected - app.packageName
                                    NotificationSettings.saveSelectedPackages(prefs, selected)
                                }
                            ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !blocked || checked)
                            Column {
                                Text(app.name)
                                Text(if (blocked) "短信／电话应用，已默认过滤" else app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showApps = false }) { Text("完成") } },
        dismissButton = { TextButton(onClick = { catalogRevision++ }) { Text("刷新列表") } }
    )
    if (showChannels) AlertDialog(
        onDismissRequest = { showChannels = false },
        title = { Text("应用通知推送通道") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val choices = listOf(null to "全部通道") + channels.map { it.id to it.name }
                items(choices) { (id, name) ->
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = selectedChannel == id, role = Role.RadioButton,
                        onValueChange = {
                            selectedChannel = id
                            prefs.edit().putString(Constants.PREF_APP_NOTIFICATION_CHANNEL_ID, id).apply()
                            showChannels = false
                        }
                    ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedChannel == id, onClick = null)
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showChannels = false }) { Text("关闭") } }
    )
}
