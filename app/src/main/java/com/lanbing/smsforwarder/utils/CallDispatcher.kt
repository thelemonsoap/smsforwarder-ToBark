package com.lanbing.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Application-scoped routing also works when the foreground service has been reclaimed. */
object CallDispatcher {
    private val handler = Handler(Looper.getMainLooper())
    private val router = CallEventRouter()
    private val deliveryQueue = CallAlertDeliveryQueue()
    private var pendingTick: Runnable? = null
    private val waitingBroadcasts = mutableListOf<() -> Unit>()
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(100))
    @Volatile private var generation = 0

    fun allowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_ENABLED, false) &&
            prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) &&
            prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    fun setSubscriptions(ids: Set<Int>) { router.setSubscriptions(ids) }

    fun resetIfDisabled(context: Context) {
        if (!allowed(context)) {
            generation++
            router.reset()
            deliveryQueue.clear()
            waitingBroadcasts.toList().forEach { it() }; waitingBroadcasts.clear()
            pendingTick?.let { handler.removeCallbacks(it) }; pendingTick = null
        }
    }

    fun onEvent(context: Context, subscriptionId: Int?, sim: String, state: CallState, number: String?,
                source: String, finish: (() -> Unit)? = null) {
        val app = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { onEvent(app, subscriptionId, sim, state, number, source, finish) }
            return
        }
        if (!allowed(app)) {
            resetIfDisabled(app)
            finish?.invoke()
            return
        }
        val result = router.onEvent(subscriptionId, sim, state, number, System.currentTimeMillis(), fromBroadcast = source == "系统广播")
        val prefs = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (result?.changed == true) {
            val text = when (state) { CallState.RINGING -> "响铃"; CallState.OFFHOOK -> "通话中"; CallState.IDLE -> "空闲" }
            val event = "${result.sim} $text（$source）".trim()
            prefs.edit().putString(Constants.PREF_CALL_LAST_EVENT, event)
                .putLong(Constants.PREF_CALL_LAST_EVENT_TIME, System.currentTimeMillis()).apply()
            LogStore.append(app, "电话状态：$event")
        }
        val routed = result?.alert
        if (routed != null && (routed.alert.kind != CallAlertKind.MISSED ||
                    prefs.getBoolean(Constants.PREF_MISSED_CALL_REMINDER_ENABLED, false))) {
            deliveryQueue.enqueue(routed, SystemClock.elapsedRealtime())
        }
        if (!number.isNullOrBlank()) LogStore.append(app, "电话号码事件：$source，${state.name}（系统已提供号码）")
        finish?.let {
            if (waitingBroadcasts.size >= 100) waitingBroadcasts.removeAt(0).invoke()
            waitingBroadcasts += it
        }
        flushPending(app)
    }

    private fun flushPending(app: Context) {
        pendingTick?.let { handler.removeCallbacks(it) }; pendingTick = null
        if (!allowed(app)) { resetIfDisabled(app); return }
        val eventGeneration = generation
        val prefs = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val deliveries = deliveryQueue.poll(SystemClock.elapsedRealtime(), router::latest).filter {
            it.routed.alert.kind != CallAlertKind.MISSED || prefs.getBoolean(Constants.PREF_MISSED_CALL_REMINDER_ENABLED, false)
        }
        val completions = if (deliveries.isNotEmpty() || !deliveryQueue.hasPending()) waitingBroadcasts.toList().also { waitingBroadcasts.clear() } else emptyList()
        val remaining = AtomicInteger(deliveries.size)
        if (deliveries.isEmpty()) completions.forEach { it() }
        deliveries.forEach { delivery ->
            val latest = delivery.routed
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(latest.alert.startedAtMillis))
            val device = listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" ")
            val text by lazy {
                val contactName = ContactNameResolver.resolve(app, latest.alert.number)
                PushMessageFactory.call(latest.alert, time, device, latest.sim, contactName, delivery.numberSupplement)
            }
            val label = if (delivery.numberSupplement) "来电号码补充" else "电话提醒"
            if (latest.alert.number.isBlank()) LogStore.append(app, "电话提醒：等待号码 3 秒后仍未获得，先发送未知号码提醒")
            sendMessage(app, label, "", eventGeneration, requirePhone = true, messageBuilder = { channel -> text.render(prefs, channel) }) {
                if (remaining.decrementAndGet() == 0) completions.forEach { it() }
            }
        }
        if (deliveryQueue.hasPending()) {
            val tick = Runnable { if (eventGeneration == generation) flushPending(app) }
            pendingTick = tick
            handler.postDelayed(tick, 100L)
        }
    }

    fun sendTest(context: Context, result: (String) -> Unit) {
        val app = context.applicationContext
        sendMessage(app, "来电测试", "【来电提醒测试】\n电话提醒的 Bark 通道测试。\n此消息仅验证发送通道，不代表系统已检测到来电。",
            generation, requirePhone = false) { message -> handler.post { result(message) } }
    }

    private fun sendMessage(context: Context, label: String, message: String, eventGeneration: Int,
                            requirePhone: Boolean, messageBuilder: ((String) -> String)? = null, complete: (String) -> Unit) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_ENABLED, false) || !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)) {
            val reason = "未发送：请先同意隐私政策并开启首页转发服务"
            LogStore.append(context, "$label$reason")
            complete(reason)
            return
        }
        val selected = prefs.getString(Constants.PREF_CALL_REMINDER_CHANNEL_ID, null)
        val channels = ChannelLoader.loadChannels(prefs).filter { selected == null || it.id == selected }
        if (channels.isEmpty()) {
            val reason = "未发送：请配置并选择电话提醒推送通道"
            LogStore.append(context, "$label$reason")
            complete(reason)
            return
        }
        val remaining = AtomicInteger(channels.size)
        val failures = AtomicInteger(0)
        fun done() {
            if (remaining.decrementAndGet() == 0) complete(if (failures.get() == 0) "发送成功" else "发送失败，请查看日志")
        }
        for (channel in channels) {
            try {
                executor.execute {
                    try {
                        val enabled = prefs.getBoolean(Constants.PREF_ENABLED, false) && prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)
                        if (!enabled || eventGeneration != generation || (requirePhone && !allowed(context))) {
                            failures.incrementAndGet()
                            return@execute
                        }
                        val sent = WebhookSender.sendTextMessage(channel.target, messageBuilder?.invoke(channel.name) ?: message, channel.type)
                        if (!sent.success) failures.incrementAndGet()
                        LogStore.append(context, "$label → ${channel.name}：${if (sent.success) "成功" else "失败：${sent.errorMessage}"}")
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                        LogStore.append(context, "$label → ${channel.name}：失败 ${e.javaClass.simpleName}")
                    } finally { done() }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                failures.incrementAndGet()
                LogStore.append(context, "${label}未发送：发送队列繁忙")
                done()
            }
        }
    }
}
