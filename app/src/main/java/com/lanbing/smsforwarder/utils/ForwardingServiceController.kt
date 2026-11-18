package com.lanbing.smsforwarder.utils

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore
import com.lanbing.smsforwarder.SmsForegroundService

object ForwardingServiceController {
    /** Call only from a visible activity / settings action; also refreshes an existing service. */
    fun ensureRunning(context: Context, diagnose: Boolean = false) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val reason = when {
            !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) -> "请先同意隐私政策"
            !prefs.getBoolean(Constants.PREF_ENABLED, false) -> "首页转发服务未开启"
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> "应用显示通知权限未开启"
            else -> null
        }
        if (reason != null) {
            if (diagnose) {
                prefs.edit().putString(Constants.PREF_CALL_LISTENER_STATUS, reason).apply()
                LogStore.append(context, "电话监听检查：$reason")
            }
            return
        }
        try {
            val intent = Intent(context, SmsForegroundService::class.java)
            if (diagnose) intent.action = SmsForegroundService.ACTION_CHECK_CALLS
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            prefs.edit().putString(Constants.PREF_CALL_LISTENER_STATUS, "服务启动失败：${e.javaClass.simpleName}").apply()
            LogStore.append(context, "恢复转发服务失败：${e.javaClass.simpleName}")
        }
    }
}
