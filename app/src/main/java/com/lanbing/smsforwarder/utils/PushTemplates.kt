package com.lanbing.smsforwarder.utils

import android.content.SharedPreferences
import android.os.Build
import com.lanbing.smsforwarder.Constants
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TemplateVariable(val name: String, val description: String) {
    val token: String get() = "{{$name}}"
}

enum class PushTemplateKind(val id: String, val title: String, val group: String, val defaultTemplate: String) {
    SMS("sms", "短信", "短信", "{{Content}}"),
    CALL_INCOMING("call_incoming", "来电", "电话", "【来电提醒】\n来电人：{{Caller}}\n来电时间：{{Time}}\n接收卡：{{Sim}}\n设备：{{Device}}"),
    CALL_MISSED("call_missed", "未接／拒接", "电话", "【未接／拒接提醒】\n来电人：{{Caller}}\n来电时间：{{Time}}\n接收卡：{{Sim}}\n设备：{{Device}}"),
    CALL_SUPPLEMENT("call_supplement", "来电号码补充", "电话", "【来电信息补充】\n来电人：{{Caller}}\n来电时间：{{Time}}\n接收卡：{{Sim}}\n系统稍后提供了此次来电号码，并非新来电。"),
    APP_NOTIFICATION("app_notification", "应用通知", "应用", "【应用通知】{{AppName}}\n标题：{{Title}}\n内容：{{Content}}\n通知时间：{{Time}}\n设备：{{Device}}"),
    BATTERY_LOW("battery_low", "低电量", "电量与充电", "【电量提醒】当前电量：{{BatteryLevel}}%，电量较低，请及时充电\n设备：{{PhoneInfo}}"),
    BATTERY_HIGH("battery_high", "高电量", "电量与充电", "【电量提醒】当前电量：{{BatteryLevel}}%，电量充足\n设备：{{PhoneInfo}}"),
    BATTERY_STEP_UP("battery_step_up", "每10%：电量上升", "电量与充电", "【电量提醒】当前电量：{{BatteryLevel}}%，{{ChargingState}}，电量{{BatteryChange}}\n{{ThresholdNote}}\n设备：{{PhoneInfo}}"),
    BATTERY_STEP_DOWN("battery_step_down", "每10%：电量下降", "电量与充电", "【电量提醒】当前电量：{{BatteryLevel}}%，{{ChargingState}}，电量{{BatteryChange}}\n{{ThresholdNote}}\n设备：{{PhoneInfo}}"),
    CHARGING_START("charging_start", "开始充电", "电量与充电", "【充电提醒】设备已开始{{ChargeType}}，当前电量：{{BatteryLevel}}%\n设备：{{PhoneInfo}}"),
    CHARGING_STOP("charging_stop", "结束充电", "电量与充电", "【充电提醒】设备已结束充电，当前电量：{{BatteryLevel}}%\n设备：{{PhoneInfo}}"),
    SIGNAL_LTE("signal_lte", "使用 LTE", "信号", "【信号提醒】现在使用{{Signal}}\n数据卡：{{Sim}}"),
    SIGNAL_5G("signal_5g", "使用 5G", "信号", "【信号提醒】现在使用{{Signal}}\n数据卡：{{Sim}}")
}

object PushTemplates {
    const val MAX_TEMPLATE_LENGTH = 20_000
    private const val MAX_ENTRIES = 300
    fun readMap(prefs: SharedPreferences, key: String): Map<String, String> = try {
        parseMap(JSONObject(prefs.getString(key, "{}") ?: "{}"))
    } catch (_: Exception) { emptyMap() }
    fun parseMap(json: JSONObject): Map<String, String> {
        require(json.length() <= MAX_ENTRIES) { "模板数量过多" }
        return json.keys().asSequence().associateWith { key ->
            val value = json.get(key)
            require(value is String && value.length <= MAX_TEMPLATE_LENGTH) { "模板必须是长度不超过20000的文本" }
            value as String
        }
    }
    /** Missing fields in an older backup leave the user's newer templates intact. */
    fun importedMap(json: JSONObject, field: String): Map<String, String>? = when {
        !json.has(field) -> null
        json.isNull(field) -> emptyMap()
        else -> parseMap(json.getJSONObject(field))
    }
    fun saved(prefs: SharedPreferences, kind: PushTemplateKind, appPackage: String? = null): String? = when {
        kind == PushTemplateKind.SMS -> prefs.getString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE, null)
        appPackage != null -> readMap(prefs, Constants.PREF_APP_PUSH_TEMPLATES)[appPackage]
        else -> readMap(prefs, Constants.PREF_PUSH_TEMPLATES)[kind.id]
    }?.takeIf { it.isNotBlank() }
    fun effective(prefs: SharedPreferences, kind: PushTemplateKind, appPackage: String? = null): String? =
        saved(prefs, kind, appPackage) ?: if (appPackage != null) saved(prefs, kind) else null
    @Synchronized fun save(prefs: SharedPreferences, kind: PushTemplateKind, template: String?, appPackage: String? = null) {
        require(template == null || template.length <= MAX_TEMPLATE_LENGTH)
        val value = template?.takeIf { it.isNotBlank() }
        if (kind == PushTemplateKind.SMS) {
            val editor = prefs.edit()
            if (value == null) editor.remove(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE) else editor.putString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE, value)
            editor.apply(); return
        }
        require(appPackage == null || kind == PushTemplateKind.APP_NOTIFICATION)
        val key = if (appPackage == null) Constants.PREF_PUSH_TEMPLATES else Constants.PREF_APP_PUSH_TEMPLATES
        val values = readMap(prefs, key).toMutableMap()
        val id = appPackage ?: kind.id
        if (value == null) values.remove(id) else values[id] = value
        require(values.size <= MAX_ENTRIES)
        prefs.edit().putString(key, JSONObject(values as Map<*, *>).toString()).apply()
    }
    fun render(template: String?, values: Map<String, String>, defaultMessage: String): String =
        if (template.isNullOrBlank()) defaultMessage
        else TemplateTokens.render(template, values + ("DefaultMessage" to defaultMessage)).ifBlank { defaultMessage }

    fun variables(kind: PushTemplateKind): List<TemplateVariable> {
        fun vars(vararg pairs: Pair<String, String>) = pairs.map { TemplateVariable(it.first, it.second) }
        val specific = when (kind) {
            PushTemplateKind.SMS -> vars("Sender" to "发送者（姓名优先）", "Content" to "短信正文", "Sim" to "本机号码", "Code" to "验证码", "Keyword" to "关键词")
            PushTemplateKind.CALL_INCOMING, PushTemplateKind.CALL_MISSED, PushTemplateKind.CALL_SUPPLEMENT ->
                vars("Caller" to "呼叫方（姓名优先）", "Sender" to "呼叫方别名（同Caller）", "ContactName" to "联系人姓名", "Sim" to "接收卡", "Content" to "原提醒正文")
            PushTemplateKind.APP_NOTIFICATION -> vars("AppName" to "应用名", "Title" to "通知标题", "Content" to "通知正文", "PackageName" to "应用包名", "Sim" to "SIM信息（应用通知不提供，值为空）")
            PushTemplateKind.SIGNAL_LTE, PushTemplateKind.SIGNAL_5G -> vars("Signal" to "网络类型", "Sim" to "数据卡", "Content" to "原提醒正文")
            else -> vars("BatteryLevel" to "当前电量（不含%）", "Threshold" to "触发档位／阈值", "Direction" to "上升或下降",
                "BatteryChange" to "变化说明", "ChargingState" to "充电状态（充电中／使用）", "ChargeType" to "充电类型", "ThresholdNote" to "同时触发的阈值提示", "PhoneInfo" to "本机SIM信息", "Sim" to "本机SIM信息别名（同PhoneInfo）", "Content" to "原提醒正文")
        }
        return specific + vars("Time" to "事件时间", "Channel" to "通道名称") +
            if (kind == PushTemplateKind.SMS) emptyList() else vars("Device" to "设备型号", "DefaultMessage" to "完整默认消息")
    }
}

data class PushMessage(val kind: PushTemplateKind, val defaultMessage: String, val values: Map<String, String>, val appPackage: String? = null) {
    fun render(prefs: SharedPreferences, channelName: String): String {
        val result = PushTemplates.render(PushTemplates.effective(prefs, kind, appPackage), values + ("Channel" to channelName), defaultMessage)
        return if (kind == PushTemplateKind.APP_NOTIFICATION) truncateUtf8(result, 3_000) else result
    }
}

object PushMessageFactory {
    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    fun device(): String = listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" ")
    private fun common(content: String, time: String, device: String, sim: String = "") =
        mapOf("Content" to content, "Time" to time, "Device" to device, "Sim" to sim)
    fun call(alert: CallAlert, time: String, device: String, sim: String, contactName: String?, supplement: Boolean): PushMessage {
        val kind = if (supplement) PushTemplateKind.CALL_SUPPLEMENT else if (alert.kind == CallAlertKind.INCOMING) PushTemplateKind.CALL_INCOMING else PushTemplateKind.CALL_MISSED
        val message = if (supplement) formatCallNumberSupplement(alert, time, sim, contactName) else formatCallAlert(alert, time, device, sim, contactName)
        val caller = ContactIdentity.label(alert.number, contactName)
        return PushMessage(kind, message, common(message,time,device,sim) + mapOf("Caller" to caller,"Sender" to caller,"ContactName" to ContactIdentity.cleanName(contactName).orEmpty()))
    }
    fun app(notification: AppNotification, time: String, device: String): PushMessage = PushMessage(
        PushTemplateKind.APP_NOTIFICATION, formatAppNotification(notification,time,device),
        common(notification.text,time,device) + mapOf("AppName" to notification.appName.ifBlank { notification.packageName }, "Title" to notification.title,"PackageName" to notification.packageName), notification.packageName)
    fun signal(signal: MobileSignal, sim: String, time: String = now(), device: String = device()): PushMessage = PushMessage(
        if (signal == MobileSignal.LTE) PushTemplateKind.SIGNAL_LTE else PushTemplateKind.SIGNAL_5G,
        formatSignalAlert(signal,sim), common(formatSignalAlert(signal,sim),time,device,sim) + ("Signal" to signal.label))
    fun battery(kind: PushTemplateKind, message: String, percent: Int, charging: Boolean, phoneInfo: String,
                threshold: Int? = null, step: BatteryStep? = null, chargeType: String = "", thresholdNote: String = "",
                time: String = now(), device: String = device()): PushMessage {
        val change = step?.let { (if (it.ascending) "上升" else "下降") + (if (percent == it.threshold) "至${it.threshold}%" else "经过${it.threshold}%档位") }.orEmpty()
        return PushMessage(kind,message,common(message,time,device,phoneInfo) + mapOf(
            "BatteryLevel" to percent.toString(), "Threshold" to (step?.threshold ?: threshold)?.toString().orEmpty(),
            "Direction" to step?.let { if (it.ascending) "上升" else "下降" }.orEmpty(), "BatteryChange" to change,
            "ChargingState" to if (charging) "充电中" else "使用", "ChargeType" to chargeType,
            "ThresholdNote" to thresholdNote, "PhoneInfo" to phoneInfo))
    }
    /** Fictional preview fixtures; never read a real SMS, caller or notification for the editor. */
    fun example(kind: PushTemplateKind, appName: String = "微信"): PushMessage = when (kind) {
        PushTemplateKind.CALL_INCOMING, PushTemplateKind.CALL_MISSED, PushTemplateKind.CALL_SUPPLEMENT -> call(
            CallAlert(if (kind == PushTemplateKind.CALL_MISSED) CallAlertKind.MISSED else CallAlertKind.INCOMING,"13800000000",0),
            "2026-11-04 12:30:00","Samsung Galaxy","SIM 1","张三",kind == PushTemplateKind.CALL_SUPPLEMENT)
        PushTemplateKind.APP_NOTIFICATION -> app(AppNotification("preview","com.example.chat",appName,"张三","会议改到下午三点。",0),"2026-11-04 12:30:00","Samsung Galaxy")
        PushTemplateKind.SIGNAL_LTE, PushTemplateKind.SIGNAL_5G -> signal(if (kind == PushTemplateKind.SIGNAL_LTE) MobileSignal.LTE else MobileSignal.FIVE_G,"SIM 1","2026-11-04 12:30:00","Samsung Galaxy")
        PushTemplateKind.SMS -> PushMessage(kind,"会议验证码：123456",mapOf("Sender" to "张三","Content" to "会议验证码：123456","Code" to "123456","Sim" to "SIM 1","Keyword" to "验证码","Time" to "2026-11-04 12:30:00","Channel" to "Bark"))
        else -> {
            val percent=when(kind) { PushTemplateKind.BATTERY_LOW -> 20; PushTemplateKind.BATTERY_HIGH -> 90; else -> 50 }
            val charging=kind != PushTemplateKind.BATTERY_STEP_DOWN && kind != PushTemplateKind.CHARGING_STOP && kind != PushTemplateKind.BATTERY_LOW
            val step=if (kind == PushTemplateKind.BATTERY_STEP_UP || kind == PushTemplateKind.BATTERY_STEP_DOWN) BatteryStep(50,kind == PushTemplateKind.BATTERY_STEP_UP) else null
            val message=when(kind) {
                PushTemplateKind.BATTERY_LOW -> "【电量提醒】当前电量：20%，电量较低，请及时充电\n设备：SIM 1"
                PushTemplateKind.BATTERY_HIGH -> "【电量提醒】当前电量：90%，电量充足\n设备：SIM 1"
                PushTemplateKind.CHARGING_START -> "【充电提醒】设备已开始USB充电，当前电量：50%\n设备：SIM 1"
                PushTemplateKind.CHARGING_STOP -> "【充电提醒】设备已结束充电，当前电量：50%\n设备：SIM 1"
                else -> formatBatteryStepReminder(50,step!!,charging,"SIM 1")
            }
            battery(kind,message,percent,charging,"SIM 1",if (kind == PushTemplateKind.BATTERY_HIGH) 90 else 20,step,
                "USB充电",time="2026-11-04 12:30:00",device="Samsung Galaxy")
        }
    }
}
