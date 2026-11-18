package com.lanbing.smsforwarder.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.utils.*
import org.json.JSONObject

internal fun insertTemplateVariable(value: TextFieldValue, token: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    return TextFieldValue(value.text.replaceRange(start,end,token),TextRange(start + token.length))
}

@Composable
fun PushTemplateSettings(smsTemplate: String, onSaveSmsTemplate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var appOverridesVisible by rememberSaveable { mutableStateOf(false) }
    val choices = remember(context) {
        val known = try { JSONObject(prefs.getString(Constants.PREF_APP_NOTIFICATION_KNOWN_APPS,"{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        NotificationSettings.selectedPackages(prefs).sorted().map { name ->
            NotificationAppChoice(name,known.optString(name).ifBlank { NotificationSettings.appName(context,name) })
        }
    }
    fun edit(kind: PushTemplateKind, appPackage: String? = null) { editingPackage = appPackage; editingId = kind.id }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp),modifier = Modifier.testTag("push-template-settings")) {
        LiquidPageHeading("推送模板", "每类单独设置，变量的位置由你决定")
        Text("支持 {{AppName}} 和 {AppName} 两种写法，不区分大小写。留空或恢复默认使用原格式。短信通道的自定义模板优先于短信默认模板。",
            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        PushTemplateKind.values().groupBy { it.group }.forEach { (group, kinds) ->
            LiquidCard(Modifier.fillMaxWidth()) {
                Text(if (group == "短信") "短信模板" else group,Modifier.padding(start=16.dp,top=16.dp,bottom=6.dp),style=MaterialTheme.typography.titleMedium)
                kinds.forEach { kind ->
                    val saved = remember(revision,smsTemplate,kind) { PushTemplates.saved(prefs,kind) }
                    TemplateSettingRow(kind.title,if (saved == null) "使用默认格式" else "已自定义", "template-row-${kind.id}") { edit(kind) }
                }
                if (group == "应用") {
                    TextButton(onClick = { appOverridesVisible = !appOverridesVisible },modifier=Modifier.padding(horizontal=8.dp).testTag("app-template-overrides")) {
                        Text(if (appOverridesVisible) "收起应用独立模板" else "按应用单独设置（${choices.size}）")
                    }
                    if (appOverridesVisible) {
                        if (choices.isEmpty()) Text("先在“应用通知”中勾选需要转发的应用。",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
                        choices.forEach { app ->
                            val saved = remember(revision,app.packageName) { PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION,app.packageName) }
                            TemplateSettingRow(app.name,if (saved == null) "沿用应用通知通用模板" else "已单独设置", "app-template-${app.packageName}") {
                                edit(PushTemplateKind.APP_NOTIFICATION,app.packageName)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Text("连续电量与高低阈值同时触发时，仍合并为一条；可在电量上升／下降模板中用 {{ThresholdNote}} 放置阈值提示。",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val kind = PushTemplateKind.values().firstOrNull { it.id == editingId }
    if (kind != null) {
        val appName = choices.firstOrNull { it.packageName == editingPackage }?.name
        val initial = if (kind == PushTemplateKind.SMS) smsTemplate.ifBlank { kind.defaultTemplate }
            else PushTemplates.effective(prefs,kind,editingPackage) ?: kind.defaultTemplate
        PushTemplateEditor(kind,appName,initial,editingPackage,
            onDismiss = { editingId = null },
            onCommit = { value ->
                try {
                    if (kind == PushTemplateKind.SMS) onSaveSmsTemplate(value.orEmpty()) else PushTemplates.save(prefs,kind,value,editingPackage)
                    revision++; editingId = null
                } catch (e: IllegalArgumentException) { Toast.makeText(context,e.message ?: "模板无法保存",Toast.LENGTH_LONG).show() }
            })
    }
}

@Composable
private fun TemplateSettingRow(title: String, status: String, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick).heightIn(min=60.dp)
        .padding(horizontal=16.dp,vertical=10.dp).testTag(tag),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.Edit,contentDescription="编辑$title 模板",modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PushTemplateEditor(kind: PushTemplateKind, appName: String?, initial: String, scope: String?, onDismiss: () -> Unit, onCommit: (String?) -> Unit) {
    var value by rememberSaveable(kind.id,scope,stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial,TextRange(initial.length))) }
    val valid = MessageTemplateRenderer.validateTemplate(value.text) && value.text.length <= PushTemplates.MAX_TEMPLATE_LENGTH
    val variables = remember(kind) { PushTemplates.variables(kind) }
    val unknown = TemplateTokens.names(value.text).filter { name -> variables.none { it.name.equals(name,ignoreCase=true) } }
    val example = remember(kind,appName) { PushMessageFactory.example(kind,appName ?: "微信") }
    val preview = remember(value.text,example) { PushTemplates.render(value.text,example.values + ("Channel" to "Bark"),example.defaultMessage) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp).heightIn(max=(LocalConfiguration.current.screenHeightDp * .90f).dp)
            .testTag("push-template-editor"),shape=GlassShapes.container,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("模板：${appName ?: kind.title}",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                    IconButton(onClick=onDismiss,modifier=Modifier.size(48.dp)) { Icon(Icons.Outlined.Close,"关闭模板编辑") }
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("template-editor-scroll"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value=value,onValueChange={ value=it },label={ Text("消息模板") },
                        modifier=Modifier.fillMaxWidth().testTag("push-template-input"),minLines=4,maxLines=10,isError=!valid,
                        supportingText={ Text(if (!valid) "请检查花括号是否成对，模板最多20000字。" else "模板决定完整正文；可自由调整文字和本类变量的位置。") })
                    if (unknown.isNotEmpty()) Text("未识别的变量将原样发送：${unknown.joinToString()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
                    TemplateVariablesMenu(variables.map { it.token to it.description },
                        onInsert={ token -> value=insertTemplateVariable(value,token) },stateKey=kind.id+scope.orEmpty())
                    Text("预览（示例数据）",style=MaterialTheme.typography.titleSmall)
                    Surface(shape=GlassShapes.container,color=MaterialTheme.colorScheme.surfaceVariant) {
                        Text(preview,Modifier.fillMaxWidth().padding(12.dp).testTag("push-template-preview"),style=MaterialTheme.typography.bodyMedium)
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Button(onClick={ onCommit(value.text) },enabled=valid,modifier=Modifier.widthIn(min=112.dp).heightIn(min=48.dp).testTag("save-push-template")) { Text("保存模板") }
                    OutlinedButton(onClick={ onCommit(null) },modifier=Modifier.heightIn(min=48.dp).testTag("reset-push-template")) { Text(if (scope == null) "恢复默认" else "沿用通用模板") }
                }
            }
        }
    }
}
