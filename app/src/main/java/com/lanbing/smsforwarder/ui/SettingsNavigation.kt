package com.lanbing.smsforwarder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    SMS("短信与过滤", "消息模板、发送者和内容名单", Icons.AutoMirrored.Outlined.Message),
    CALLS("来电与通讯录", "来电提醒、联系人姓名", Icons.Outlined.Phone),
    APPS("应用通知", "应用选择与去重", Icons.Outlined.Notifications),
    BATTERY("电量提醒", "连续电量、阈值与充电", Icons.Outlined.BatteryChargingFull),
    SIGNAL("信号提醒", "LTE / 5G、Wi-Fi 与波动过滤", Icons.Outlined.NetworkCell),
    APPEARANCE("外观与小组件", "沉浸模式、主题与桌面显示", Icons.Outlined.Palette),
    PERMISSIONS("SIM卡与权限", "号码信息、授权与后台运行", Icons.Outlined.Security),
    TOOLS("备份与测试", "导入、导出与规则测试", Icons.Outlined.Backup),
    PRIVACY("隐私与数据", "隐私政策与授权管理", Icons.Outlined.PrivacyTip),
    TEMPLATES("推送模板", "分别设置每类消息与应用的格式", Icons.Outlined.EditNote)
}

@Composable
fun SettingsHub(state: LazyListState, onOpen: (SettingsSection) -> Unit) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("settings-hub"),
        contentPadding = glassPagePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "heading") { LiquidPageHeading("设置", "选择一项进行设置") }
        listOf(
            listOf(SettingsSection.SMS, SettingsSection.TEMPLATES, SettingsSection.CALLS, SettingsSection.APPS, SettingsSection.BATTERY, SettingsSection.SIGNAL),
            listOf(SettingsSection.APPEARANCE, SettingsSection.PERMISSIONS),
            listOf(SettingsSection.TOOLS, SettingsSection.PRIVACY)
        ).forEachIndexed { index, sections ->
            item(key = "group-$index") {
                LiquidCard(Modifier.fillMaxWidth()) {
                    sections.forEachIndexed { row, section ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(section) }
                                .heightIn(min = 66.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("settings-entry-${section.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(section.icon, contentDescription = null, modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(section.title, style = MaterialTheme.typography.titleMedium)
                                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        if (row < sections.lastIndex) HorizontalDivider(Modifier.padding(start = 51.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    }
                }
            }
        }
    }
}
