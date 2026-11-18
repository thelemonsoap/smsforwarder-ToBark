/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder

/**
 * 应用导出配置数据类
 *
 * 封装导出/导入所需的全部配置项，避免函数参数列表过长（原 16 个参数）。
 * 所有与配置导出相关的函数均使用此类作为参数载体。
 */
data class ExportConfig(
    val channels: List<Channel>,
    val keywordConfigs: List<KeywordConfig>,
    val showReceiverPhone: Boolean,
    val showSenderPhone: Boolean,
    val highlightVerificationCode: Boolean,
    val globalMessageTemplate: String? = null,
    val batteryStepReminderEnabled: Boolean = false,
    val batteryReminderEnabled: Boolean,
    val lowBatteryReminderEnabled: Boolean,
    val highBatteryReminderEnabled: Boolean,
    val chargingReminderEnabled: Boolean,
    val batteryReminderChannelId: String?,
    val lowBatteryThreshold: Int,
    val highBatteryThreshold: Int,
    val customSim1Phone: String?,
    val customSim2Phone: String?,
    val startOnBoot: Boolean,
    val senderWhitelist: List<String> = emptyList(),
    val senderBlacklist: List<String> = emptyList(),
    val contentWhitelist: List<String> = emptyList(),
    val contentBlacklist: List<String> = emptyList(),
    val callReminderEnabled: Boolean = false,
    val missedCallReminderEnabled: Boolean = false,
    val callReminderChannelId: String? = null,
    val signalReminderEnabled: Boolean = false,
    val signalReminderChannelId: String? = null,
    val signalSendInitial: Boolean = true,
    val signalPauseDuringCalls: Boolean = true,
    val appNotificationEnabled: Boolean = false,
    val appNotificationPackages: List<String> = emptyList(),
    val appNotificationChannelId: String? = null,
    val appNotificationSkipPhoneSms: Boolean = true,
    val appNotificationSkipOngoing: Boolean = true,
    val contactNameEnabled: Boolean = false,
    val pushTemplates: Map<String, String> = emptyMap(),
    val appNotificationTemplates: Map<String, String> = emptyMap()
)
