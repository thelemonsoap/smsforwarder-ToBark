package com.lanbing.smsforwarder

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.lanbing.smsforwarder.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Android binds this service only after the user grants Notification access in system settings. */
class AppNotificationListenerService : NotificationListenerService() {
    private val prefs by lazy { getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var deduplicator: NotificationDeduplicator
    private val names = mutableMapOf<String, String>()
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(100))
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    override fun onCreate() {
        super.onCreate()
        deduplicator = NotificationDeduplicator(restored = restoreFingerprints())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Do not enumerate/replay active or historical notifications when access is first granted.
        if (NotificationSettings.forwardingEnabled(prefs)) LogStore.append(this, "应用通知监听已连接")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationSettings.requestConnection(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleNotification(sbn) }
        } else handleNotification(sbn)
    }

    private fun handleNotification(sbn: StatusBarNotification?) {
        if (closed || sbn == null || !NotificationSettings.forwardingEnabled(prefs) || sbn.packageName == packageName) return
        try {
            val source = sbn.packageName
            val name = names.getOrPut(source) { NotificationSettings.appName(this, source) }
            NotificationSettings.rememberApp(prefs, source, name)
            val options = currentOptions()
            // Do not access the title/body of apps the user has not selected.
            if (source !in options.selectedPackages ||
                (options.skipPhoneAndSms && source in options.phoneAndSmsPackages)) return
            val notification = sbn.notification ?: return
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
                (options.skipOngoing && notification.flags and
                    (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0)) return
            val extracted = extractNotification(sbn, name)
            if (!acceptsNotification(extracted, options)) return
            if (targetChannels().isEmpty()) {
                LogStore.append(this, "应用通知未转发：请配置并选择推送通道")
                return
            }
            if (!deduplicator.shouldForward(extracted, System.currentTimeMillis())) return
            persistFingerprints()
            try {
                executor.execute { forward(extracted) }
            } catch (_: RejectedExecutionException) {
                deduplicator.forget(extracted.key)
                persistFingerprints()
                LogStore.append(this, "应用通知转发繁忙：队列已满，请查看网络状态")
            }
        } catch (e: Exception) {
            LogStore.append(this, "应用通知处理失败：${e.javaClass.simpleName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { forgetNotification(sbn) }
        } else forgetNotification(sbn)
    }

    private fun forgetNotification(sbn: StatusBarNotification?) {
        if (!closed && sbn != null && ::deduplicator.isInitialized) {
            if (deduplicator.forget(sbn.key)) persistFingerprints()
        }
    }

    private fun currentOptions() = NotificationForwardingOptions(
        enabled = NotificationSettings.forwardingEnabled(prefs),
        selectedPackages = NotificationSettings.selectedPackages(prefs),
        selfPackage = packageName,
        phoneAndSmsPackages = NotificationSettings.phoneAndSmsPackages(this),
        skipPhoneAndSms = prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_PHONE_SMS, true),
        skipOngoing = prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_SKIP_ONGOING, true)
    )

    private fun targetChannels(): List<Channel> {
        val id = prefs.getString(Constants.PREF_APP_NOTIFICATION_CHANNEL_ID, null)
        return ChannelLoader.loadChannels(prefs).filter { id == null || it.id == id }
    }

    @Suppress("DEPRECATION")
    private fun extractNotification(sbn: StatusBarNotification, name: String): AppNotification {
        val notification = sbn.notification
        val extras = notification.extras
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        var text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        if (text.isBlank()) text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        var messageTime = 0L
        val latest = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages?.lastOrNull()
        if (latest != null) {
            val sender = latest.person?.name?.toString().orEmpty().ifBlank { latest.sender?.toString().orEmpty() }
            val content = latest.text?.toString().orEmpty().ifBlank { if (latest.dataUri != null) "[媒体消息]" else "" }
            text = if (sender.isBlank()) content else "$sender：$content"
            messageTime = latest.timestamp
            if (title.isBlank()) title = sender
        }
        if (text.isBlank()) {
            text = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString().orEmpty().trim()
        }
        if (text.isBlank()) text = notification.tickerText?.toString().orEmpty().trim()
        return AppNotification(
            key = sbn.key, packageName = sbn.packageName, appName = name,
            title = title, text = text, postedAt = sbn.postTime,
            messageTime = messageTime, notificationTime = notification.`when`,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            ongoing = notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        )
    }

    private fun forward(notification: AppNotification) {
        if (closed || !NotificationSettings.isAccessGranted(this) || !acceptsNotification(notification, currentOptions())) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(notification.postedAt))
        val device = listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" ")
        val message = PushMessageFactory.app(notification, time, device)
        targetChannels().forEach { channel ->
            if (closed || !NotificationSettings.forwardingEnabled(prefs)) return
            try {
                val result = WebhookSender.sendTextMessage(channel.target, message.render(prefs, channel.name), channel.type)
                // Do not copy a server response that might echo notification text into local logs.
                val status = if (result.success) "成功" else "失败：${result.errorMessage.substringBefore(':').take(80)}"
                LogStore.append(this, "应用通知 ${notification.appName} → ${channel.name}：$status")
            } catch (e: Exception) {
                LogStore.append(this, "应用通知 ${notification.appName} → ${channel.name}：失败 ${e.javaClass.simpleName}")
            }
        }
    }

    private fun restoreFingerprints(): List<NotificationFingerprint> = try {
        val array = JSONArray(prefs.getString(Constants.PREF_APP_NOTIFICATION_FINGERPRINTS, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            NotificationFingerprint(item.getString("key"), item.getString("identity"), item.getString("content"), item.getLong("time"))
        }
    } catch (_: Exception) { emptyList() }

    private fun persistFingerprints() {
        val array = JSONArray()
        deduplicator.snapshot().forEach { entry ->
            array.put(JSONObject().put("key", entry.key).put("identity", entry.identityHash)
                .put("content", entry.contentHash).put("time", entry.recordedAt))
        }
        prefs.edit().putString(Constants.PREF_APP_NOTIFICATION_FINGERPRINTS, array.toString()).apply()
    }

    override fun onDestroy() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }
}
