package com.lanbing.smsforwarder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants

@Composable
fun ContactNameCard(permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false)) }
    var revision by remember { mutableStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Constants.PREF_CONTACT_NAME_ENABLED) enabled = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        revision++
    }
    LaunchedEffect(permissionUpdateTrigger) {
        enabled = prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false)
        revision++
    }
    val granted = remember(permissionUpdateTrigger, revision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    LiquidCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("通讯录姓名", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("短信和电话优先显示联系人", Modifier.weight(1f))
                Switch(enabled, onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean(Constants.PREF_CONTACT_NAME_ENABLED, it).apply()
                    if (it && !granted) permission.launch(Manifest.permission.READ_CONTACTS)
                })
            }
            Text("匹配成功只显示姓名，不附带对方号码；未匹配、号码被隐藏或未授权时继续显示号码或未知号码。短信筛选和去重仍使用原号码。", style = MaterialTheme.typography.bodySmall)
            Text("仅在需要转发短信或电话时按号码查询本机通讯录，匹配姓名会发送到您配置的推送通道。不扫描、上传整本通讯录，也不保存联系人姓名。", style = MaterialTheme.typography.bodySmall)
            Text("短信默认通知会显示发件人；自定义模板请使用 {sender}。关闭“显示发件人”后，默认短信通知同时隐藏姓名和号码。", style = MaterialTheme.typography.bodySmall)
            if (enabled) {
                Text("通讯录权限：${if (granted) "已授权" else "未授权，使用号码提醒"}")
                if (!granted) OutlinedButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("授权读取通讯录") }
                if (!granted && denied) OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("打开应用权限设置") }
            }
        }
    }
}
