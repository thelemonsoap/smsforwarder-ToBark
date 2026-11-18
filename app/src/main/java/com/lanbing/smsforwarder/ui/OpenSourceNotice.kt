package com.lanbing.smsforwarder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OpenSourceNotice() {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("开源来源与修改说明", fontWeight = FontWeight.Bold)
        Text("本应用基于开源项目 SmsForwarder.cn v2.9.8 修改，为 Bark 兼容定制版本，非原项目官方发布版本。", style = MaterialTheme.typography.bodySmall)
        Text("原项目：huahaotech/smsforwarder.cn\n原著作权人：华昊科技有限公司\n原开发者：王士辉\n原项目许可证：MIT License", style = MaterialTheme.typography.bodySmall)
        Text("本定制版修改：简洁应用图标与服务状态、最近发送消息桌面小组件（半透明黑底与细磨砂纹理，无定时刷新）、沉浸系统栏、紧凑滑块导航与应用内基于Haze的真实背景模糊、浅深色主题与减少透明效果、Bark 推送兼容、电话提醒与号码补全、应用通知转发、LTE／5G 信号提醒及5分钟波动过滤、Wi-Fi连接期间暂停信号检测、通话至结束后30秒的信号静默及每10%连续电量提醒；通讯录姓名匹配、按需失败重试、日志追加写入与电量信息按需获取。", style = MaterialTheme.typography.bodySmall)
        Text("本次扩展：短信、电话、应用通知、电量／充电及LTE／5G可分别编辑推送模板；支持按应用覆盖通用模板、变量插入与示例预览，配置可导入导出。", style = MaterialTheme.typography.bodySmall)
        Text("背景模糊：Haze 0.5.0 · Christopher Banes 及贡献者 · Apache License 2.0。许可文件随应用提供。", style = MaterialTheme.typography.bodySmall)
        Text("查看原项目源码", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huahaotech/smsforwarder.cn")))
        })
        TextButton(onClick = { showLicense = true }) { Text("查看原项目版权与 MIT 许可全文") }
    }
    if (showLicense) {
        val license = remember { context.assets.open("upstream_license.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("原项目版权与 MIT 许可证") },
            text = { SelectionContainer { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(license, style = MaterialTheme.typography.bodySmall)
            } } }, confirmButton = { TextButton(onClick = { showLicense = false }) { Text("关闭") } })
    }
}
