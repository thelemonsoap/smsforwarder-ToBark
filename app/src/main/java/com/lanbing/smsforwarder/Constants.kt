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
 * 应用常量
 */
object Constants {
    const val PREF_SIGNAL_WIFI_BASELINE_PENDING = "signal_wifi_baseline_pending"
    const val PREF_CONTACT_NAME_ENABLED = "contact_name_enabled"
    // 日志
    const val LOG_FILE_NAME = "sms_forwarder_logs.txt"
    const val MAX_LOG_ENTRIES = 200
    const val MAX_LOG_LINE_LENGTH = 2000

    // 去重
    const val DUPLICATE_WINDOW_MS = 5000L

    // 重试时间表：30秒、1分钟、2分钟、5分钟，超过5分钟放弃
    val RETRY_SCHEDULE = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)
    const val MAX_FAILED_MESSAGES = 100
    const val FAILED_MESSAGES_FILE = "failed_messages.json"

    // 线程
    const val THREAD_POOL_SIZE = 4
    const val BROADCAST_TIMEOUT_SECONDS = 45L

    // 通知
    const val NOTIFICATION_UPDATE_THROTTLE_MS = 1000L
    const val NOTIFICATION_CHANNEL_ID = "sms_forwarder_channel"
    const val NOTIFICATION_CHANNEL_NAME = "短信转发服务"
    const val NOTIFICATION_ID = 1423

    // 偏好设置键
    const val PREFS_NAME = "app_config"
    const val PREF_ENABLED = "enabled"
    const val PREF_START_ON_BOOT = "start_on_boot"
    const val PREF_CHANNELS = "channels"
    const val PREF_KEYWORD_CONFIGS = "keyword_configs"
    // 消息格式（已弃用，保留以兼容旧版本）
    const val PREF_SHOW_RECEIVER_PHONE = "show_receiver_phone"
    const val PREF_SHOW_SENDER_PHONE = "show_sender_phone"
    const val PREF_HIGHLIGHT_VERIFICATION_CODE = "highlight_verification_code"

    // 短信默认模板（通道未自定义短信模板时使用）
    const val PREF_GLOBAL_MESSAGE_TEMPLATE = "global_message_template"
    // 其他自动推送正文模板：按类型设置，以及应用通知的按应用覆盖。
    const val PREF_PUSH_TEMPLATES = "push_message_templates"
    const val PREF_APP_PUSH_TEMPLATES = "app_push_message_templates"
    const val PREF_CUSTOM_SIM1_PHONE = "custom_sim1_phone"
    const val PREF_CUSTOM_SIM2_PHONE = "custom_sim2_phone"
    const val PREF_PRIVACY_AGREED = "privacy_agreed"

    // 发送者过滤（全局黑白名单，JSON 数组字符串存储）
    const val PREF_SENDER_WHITELIST = "sender_whitelist"
    const val PREF_SENDER_BLACKLIST = "sender_blacklist"

    // 内容过滤（全局黑白名单，JSON 数组字符串存储）
    const val PREF_CONTENT_WHITELIST = "content_whitelist"
    const val PREF_CONTENT_BLACKLIST = "content_blacklist"

    // 电话提醒（默认关闭；号码权限单独授权）
    const val PREF_SIGNAL_REMINDER_ENABLED = "signal_reminder_enabled"
    const val PREF_SIGNAL_CHANNEL_ID = "signal_channel_id"
    const val PREF_SIGNAL_SEND_INITIAL = "signal_send_initial"
    const val PREF_SIGNAL_PAUSE_DURING_CALLS = "signal_pause_during_calls"
    const val PREF_SIGNAL_CALL_ACTIVE = "signal_call_active"
    const val PREF_SIGNAL_CALL_QUIET_UNTIL = "signal_call_quiet_until"
    const val PREF_SIGNAL_LAST_LABEL = "signal_last_label"
    const val PREF_SIGNAL_LAST_SUB_ID = "signal_last_sub_id"
    const val PREF_SIGNAL_LAST_TIME = "signal_last_time"
    const val PREF_SIGNAL_STATUS = "signal_status"
    const val PREF_CALL_REMINDER_ENABLED = "call_reminder_enabled"
    const val PREF_MISSED_CALL_REMINDER_ENABLED = "missed_call_reminder_enabled"
    const val PREF_CALL_REMINDER_CHANNEL_ID = "call_reminder_channel_id"
    // Diagnostic state, not exported as configuration.
    const val PREF_CALL_LISTENER_STATUS = "call_listener_status"
    const val PREF_CALL_LAST_EVENT = "call_last_event"
    const val PREF_CALL_LAST_EVENT_TIME = "call_last_event_time"

    // 应用通知转发（默认关闭，应用白名单为空）
    const val PREF_APP_NOTIFICATION_ENABLED = "app_notification_enabled"
    const val PREF_APP_NOTIFICATION_PACKAGES = "app_notification_packages"
    const val PREF_APP_NOTIFICATION_CHANNEL_ID = "app_notification_channel_id"
    const val PREF_APP_NOTIFICATION_SKIP_PHONE_SMS = "app_notification_skip_phone_sms"
    const val PREF_APP_NOTIFICATION_SKIP_ONGOING = "app_notification_skip_ongoing"
    const val PREF_APP_NOTIFICATION_KNOWN_APPS = "app_notification_known_apps"
    const val PREF_APP_NOTIFICATION_FINGERPRINTS = "app_notification_fingerprints"

    // 电量提醒
    const val PREF_BATTERY_STEP_ENABLED = "battery_step_enabled"
    const val PREF_BATTERY_STEP_PREVIOUS = "battery_step_previous"
    const val PREF_BATTERY_STEP_LAST_THRESHOLD = "battery_step_last_threshold"
    const val PREF_BATTERY_STEP_LAST_DIRECTION = "battery_step_last_direction"
    const val PREF_BATTERY_REMINDER_ENABLED = "battery_reminder_enabled"
    const val PREF_LOW_BATTERY_REMINDER_ENABLED = "low_battery_reminder_enabled"
    const val PREF_HIGH_BATTERY_REMINDER_ENABLED = "high_battery_reminder_enabled"
    const val PREF_CHARGING_REMINDER_ENABLED = "charging_reminder_enabled"
    const val PREF_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
    const val PREF_HIGH_BATTERY_THRESHOLD = "high_battery_threshold"
    const val PREF_LAST_LOW_BATTERY_REMIND_LEVEL = "last_low_battery_remind_level"
    const val PREF_LAST_HIGH_BATTERY_REMIND_LEVEL = "last_high_battery_remind_level"
    const val PREF_LAST_CHARGING_STATE = "last_charging_state"
    const val PREF_LAST_CHARGING_STATE_INITIALIZED = "last_charging_state_initialized"
    const val PREF_BATTERY_REMINDER_CHANNEL_ID = "battery_reminder_channel_id"
    const val DEFAULT_LOW_BATTERY_THRESHOLD = 10
    const val DEFAULT_HIGH_BATTERY_THRESHOLD = 90

    // 网络
    const val NETWORK_DEBOUNCE_MS = 2000L
    const val CALL_TIMEOUT_SECONDS = 20L
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 20L
}
