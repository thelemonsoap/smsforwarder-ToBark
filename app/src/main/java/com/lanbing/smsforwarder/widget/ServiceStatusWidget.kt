package com.lanbing.smsforwarder.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.MainActivity
import com.lanbing.smsforwarder.R
import com.lanbing.smsforwarder.SmsForegroundService

/** Reads current process state, never persists a stale "running" flag or starts a service. */
class ServiceStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, force = true)
    }

    companion object {
        @Suppress("DEPRECATION")
        internal fun isServiceRunning(context: Context): Boolean = try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Android O+ exposes this application's own services, which is exactly what we need.
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == SmsForegroundService::class.java.name && it.foreground
            }
        } catch (_: Exception) { false }

        internal fun statusText(agreed: Boolean, enabled: Boolean, running: Boolean): String = when {
            !agreed -> "请先完成设置"
            !enabled -> "转发已关闭"
            running -> "服务运行中"
            else -> "服务未运行"
        }

        internal fun buildViews(context: Context, running: Boolean, latest: SentMessageStore.Snapshot = SentMessageStore.read(context)): RemoteViews {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val agreed = prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)
            val enabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
            val healthy = agreed && enabled && running
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            return RemoteViews(context.packageName, R.layout.widget_service_status).apply {
                setTextViewText(R.id.widget_status, statusText(agreed, enabled, running))
                setTextColor(R.id.widget_status, Color.parseColor(if (healthy) "#91EFCA" else "#F1F7FF"))
                setTextViewText(R.id.widget_detail, if (!agreed) "请先完成应用设置" else if (latest.sentAt > 0L)
                    "最近发送 · " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(latest.sentAt))
                    else "暂无成功发送的消息")
                setTextViewText(R.id.widget_message, if (agreed && latest.sentAt > 0L) latest.text else "发送成功后在此显示消息内容")
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 1501,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags))

            }
        }

        private val lastRendered = mutableMapOf<Int, String>()

        @Synchronized fun updateAll(context: Context, running: Boolean? = null, force: Boolean = false) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ServiceStatusWidget::class.java))
                if (ids.isEmpty()) { lastRendered.clear(); return }
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val agreed = prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)
                val enabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
                val active = running ?: isServiceRunning(context)
                val latest = SentMessageStore.read(context)
                val state = statusText(agreed, enabled, active) + "\n" +
                    (if (agreed) "${latest.sentAt}\n${latest.text}" else "")
                val changed = ids.filter { force || lastRendered[it] != state }
                if (changed.isEmpty()) return
                manager.updateAppWidget(changed.toIntArray(), buildViews(context, active, latest))
                changed.forEach { lastRendered[it] = state }
                lastRendered.keys.retainAll(ids.toSet())
            } catch (_: Exception) { /* Launcher problems must not affect forwarding. */ }
        }
    }
}
