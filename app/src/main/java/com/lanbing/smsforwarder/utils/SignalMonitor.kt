package com.lanbing.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore
import com.lanbing.smsforwarder.ForwardErrorType
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SignalMonitor(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(16))
    private data class Registration(val id: Int, val sim: String, val identity: Any, val detach: () -> Unit)
    private var registration: Registration? = null
    private var tracker: SignalTransitionTracker? = null
    private var pending: Runnable? = null
    @Volatile private var latestObserved: MobileSignal? = null
    private var lastPauseSetting = prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)
    private var activeDataId: Int? = null
    private var subscriptions: SubscriptionManager? = null
    private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var lastStatus: String? = null
    private val wifiMonitor = WifiSignalMonitor(context) { updateRegistration() }
    private var lastWifiEpoch: Long? = null
    @Volatile private var generation = 0
    @Volatile private var closed = false
    @Volatile private var stable: MobileSignal? = null
    @Volatile private var notificationEpoch = 0

    private val quietListener: () -> Unit = {
        // Phone entry points and the expiry timer run on main; invalidate workers immediately.
        if (prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)) {
            notificationEpoch++
            rebaseQuiet()
        }
    }
    init { SignalCallMute.addListener(quietListener) }

    private fun quiet() = SignalCallMute.muted(context)

    private fun cache(signal: MobileSignal) {
        val reg = registration ?: return
        val changed = stable != signal || prefs.getString(Constants.PREF_SIGNAL_LAST_LABEL, null) != signal.label ||
            prefs.getInt(Constants.PREF_SIGNAL_LAST_SUB_ID, Int.MIN_VALUE) != reg.id
        stable = signal
        if (prefs.getBoolean(Constants.PREF_SIGNAL_WIFI_BASELINE_PENDING, false)) {
            prefs.edit().remove(Constants.PREF_SIGNAL_WIFI_BASELINE_PENDING).apply()
        }
        if (changed) prefs.edit().putString(Constants.PREF_SIGNAL_LAST_LABEL, signal.label)
            .putInt(Constants.PREF_SIGNAL_LAST_SUB_ID, reg.id).putLong(Constants.PREF_SIGNAL_LAST_TIME, System.currentTimeMillis()).apply()
    }

    private fun rebaseQuiet() {
        pending?.let { handler.removeCallbacks(it) }; pending = null
        tracker?.let { state ->
            state.rebase(latestObserved ?: state.stable)
            state.stable?.let { cache(it) }
        }
    }

    private fun allowed() = !closed && prefs.getBoolean(Constants.PREF_ENABLED, false) &&
        prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) &&
        prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun updateRegistration(diagnose: Boolean = false) {
        if (closed) return
        val pauseSetting = prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)
        if (pauseSetting != lastPauseSetting) {
            lastPauseSetting = pauseSetting
            notificationEpoch++
            rebaseQuiet()
        }
        if (!allowed()) {
            wifiMonitor.stop()
            lastWifiEpoch = null
            stopListening()
            val reason = when {
                !prefs.getBoolean(Constants.PREF_ENABLED, false) -> "首页转发未开启"
                !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) -> "请先同意隐私政策"
                !prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false) -> "信号提醒未开启"
                else -> "缺少电话状态权限，无法识别移动网络"
            }
            status(reason, diagnose)
            return
        }
        if (!wifiMonitor.start()) {
            stopListening()
            status("Wi-Fi 状态监听失败，信号检测已暂停；可点击重新检查信号", diagnose)
            return
        }
        val wifiEpoch = wifiMonitor.epoch
        if (lastWifiEpoch != wifiEpoch) {
            val crossedWifi = lastWifiEpoch != null || wifiMonitor.connected
            lastWifiEpoch = wifiEpoch
            if (crossedWifi) pauseForWifi()
        }
        if (wifiMonitor.connected) {
            pauseForWifi()
            status("已连接 Wi-Fi：暂停 LTE／5G 检测和信号提醒", diagnose)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (base == null) { status("系统电话服务不可用", diagnose); return }
        var id = -1
        var sim = "默认数据卡"
        if (Build.VERSION.SDK_INT >= 24) {
            subscriptions = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                val infos = subscriptions?.activeSubscriptionInfoList.orEmpty()
                if (infos.isNotEmpty() && activeDataId != null && infos.none { it.subscriptionId == activeDataId }) activeDataId = null
                id = activeDataId ?: SubscriptionManager.getDefaultDataSubscriptionId()
                infos.find { it.subscriptionId == id }?.let { sim = "SIM ${it.simSlotIndex + 1}" }
            } catch (e: Exception) { LogStore.append(context, "信号提醒：读取数据卡失败 ${e.javaClass.simpleName}") }
        }
        if (diagnose || registration?.id != id) {
            detachCurrent()
            val manager = if (Build.VERSION.SDK_INT >= 24 && id >= 0) base.createForSubscriptionId(id) else base
            register(manager, id, sim)
        }
        if (Build.VERSION.SDK_INT >= 24 && subscriptionListener == null) {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() { if (allowed()) updateRegistration() }
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) subscriptions?.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
                else subscriptions?.addOnSubscriptionsChangedListener(listener)
                subscriptionListener = listener
            } catch (e: Exception) { LogStore.append(context, "信号提醒：SIM 监听失败 ${e.javaClass.simpleName}") }
        }
        val selected = prefs.getString(Constants.PREF_SIGNAL_CHANNEL_ID, null)
        val channels = ChannelLoader.loadChannels(prefs).filter { selected == null || it.id == selected }
        status(if (registration == null) "信号监听注册失败，请检查电话权限" else
            "信号监听已开启：$sim" + if (channels.isEmpty()) "；未选择有效推送通道" else "；通道 ${channels.joinToString { it.name }}", diagnose)
    }

    private fun register(manager: TelephonyManager, id: Int, sim: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val identity = Any()
        val currentGeneration = generation
        val needsWifiBaseline = prefs.getBoolean(Constants.PREF_SIGNAL_WIFI_BASELINE_PENDING, false)
        val saved = prefs.getString(Constants.PREF_SIGNAL_LAST_LABEL, null)?.let { label -> MobileSignal.values().find { it.label == label } }
            ?.takeIf { prefs.getInt(Constants.PREF_SIGNAL_LAST_SUB_ID, Int.MIN_VALUE) == id }
        tracker = SignalWifiRules.tracker(saved, prefs.getBoolean(Constants.PREF_SIGNAL_SEND_INITIAL, true), needsWifiBaseline)
        stable = tracker?.stable
        if (quiet()) tracker?.rebase(stable)
        var hasDisplayInfo = false
        fun current() = registration?.identity === identity && currentGeneration == generation && allowed() && !wifiMonitor.connected
        fun receive(network: Int, override: Int, display: Boolean) {
            if (!current()) return
            if (display) hasDisplayInfo = true
            if (display || !hasDisplayInfo) observe(classifyMobileSignal(network, override), currentGeneration)
        }
        fun dataChanged(next: Int) {
            if (current() && next != activeDataId) {
                activeDataId = next.takeIf { it >= 0 }
                handler.post { if (current()) updateRegistration() }
            }
        }
        try {
            when {
                Build.VERSION.SDK_INT >= 31 -> {
                    val callback = ModernCallback({ n -> receive(n, 0, false) }, { n, o -> receive(n, o, true) }, ::dataChanged)
                    registration = Registration(id, sim, identity) { manager.unregisterTelephonyCallback(callback) }
                    manager.registerTelephonyCallback(context.mainExecutor, callback)
                }
                Build.VERSION.SDK_INT >= 30 -> {
                    val listener = DisplayListener({ n -> receive(n, 0, false) }, { n, o -> receive(n, o, true) }, ::dataChanged)
                    registration = Registration(id, sim, identity) { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
                    manager.listen(listener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED or PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE)
                }
                Build.VERSION.SDK_INT >= 29 -> {
                    val listener = DataSimListener({ n -> receive(n, 0, false) }, ::dataChanged)
                    registration = Registration(id, sim, identity) { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
                    manager.listen(listener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE)
                }
                else -> {
                    val listener = LegacyListener { n -> receive(n, 0, false) }
                    registration = Registration(id, sim, identity) { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
                    manager.listen(listener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
                }
            }
            try {
                val initial = if (Build.VERSION.SDK_INT >= 24) manager.dataNetworkType else manager.networkType
                receive(initial, 0, false)
            } catch (e: Exception) { LogStore.append(context, "信号提醒：当前网络读取失败，等待系统回调 ${e.javaClass.simpleName}") }
        } catch (e: Exception) {
            detachCurrent()
            LogStore.append(context, "信号提醒注册失败：${e.javaClass.simpleName}")
        }
    }

    private fun observe(signal: MobileSignal?, eventGeneration: Int) {
        if (wifiMonitor.connected || lastWifiEpoch != wifiMonitor.epoch) { updateRegistration(); return }
        val state = tracker ?: return
        latestObserved = signal
        if (quiet()) { rebaseQuiet(); return }
        state.observe(signal, SystemClock.elapsedRealtime())
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        val deadline = state.deadline() ?: return
        val runnable = Runnable {
            pending = null
            if (eventGeneration != generation || !allowed()) return@Runnable
            if (wifiMonitor.connected || lastWifiEpoch != wifiMonitor.epoch) { updateRegistration(); return@Runnable }
            if (quiet()) { rebaseQuiet(); return@Runnable }
            val decision = state.poll(SystemClock.elapsedRealtime()) ?: return@Runnable
            val reg = registration ?: return@Runnable
            cache(decision.signal)
            notificationEpoch++
            LogStore.append(context, "移动网络已确认：${reg.sim} ${decision.signal.label}")
            if (decision.notify) send(decision.signal, reg.sim, eventGeneration)
        }
        pending = runnable
        handler.postDelayed(runnable, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

    private fun send(signal: MobileSignal, sim: String, eventGeneration: Int, attempt: Int = 0) {
        if (eventGeneration != generation || !allowed() || quiet() || stable != signal || wifiMonitor.connected) return
        val selected = prefs.getString(Constants.PREF_SIGNAL_CHANNEL_ID, null)
        val epoch = notificationEpoch
        val wifiEpoch = wifiMonitor.epoch
        val callEpoch = if (prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)) SignalCallMute.epoch else null
        val channels = ChannelLoader.loadChannels(prefs).filter { selected == null || it.id == selected }
        if (channels.isEmpty()) { LogStore.append(context, "信号提醒未发送：请配置并选择推送通道"); return }
        val message = PushMessageFactory.signal(signal, sim)
        channels.forEach { channel ->
            fun deliver(retry: Int) {
                try {
                    executor.execute {
                        if (!SignalWifiRules.mayDeliver(wifiMonitor.connected, wifiMonitor.epoch, wifiEpoch) || wifiMonitor.connectedForDelivery() || eventGeneration != generation || epoch != notificationEpoch || (callEpoch != null && callEpoch != SignalCallMute.epoch) || quiet() || !allowed() || stable != signal || latestObserved != signal || selected != prefs.getString(Constants.PREF_SIGNAL_CHANNEL_ID, null)) return@execute
                        var retryable = false
                        try {
                            val result = WebhookSender.sendTextMessage(channel.target, message.render(prefs, channel.name), channel.type)
                            LogStore.append(context, "信号提醒 ${signal.label} → ${channel.name}：${if (result.success) "成功" else "失败：${result.errorMessage}"}")
                            retryable = !result.success && result.errorType == ForwardErrorType.RETRYABLE
                        } catch (e: Exception) {
                            retryable = true
                            LogStore.append(context, "信号提醒 ${signal.label} → ${channel.name}：失败 ${e.javaClass.simpleName}")
                        }
                        if (retryable && retry < 2) handler.postDelayed({ if (SignalWifiRules.mayDeliver(wifiMonitor.connected, wifiMonitor.epoch, wifiEpoch) && eventGeneration == generation && epoch == notificationEpoch && (callEpoch == null || callEpoch == SignalCallMute.epoch) && !quiet() && allowed() && stable == signal && latestObserved == signal) deliver(retry + 1) }, if (retry == 0) 5_000L else 15_000L)
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) { LogStore.append(context, "信号提醒未发送：发送队列繁忙") }
            }
            deliver(attempt)
        }
    }

    private fun pauseForWifi() {
        if (!prefs.getBoolean(Constants.PREF_SIGNAL_WIFI_BASELINE_PENDING, false)) {
            prefs.edit().putBoolean(Constants.PREF_SIGNAL_WIFI_BASELINE_PENDING, true).apply()
        }
        // Remove data/display callbacks, SIM-change callback and pending confirmation; invalidate queued sends.
        if (registration != null || tracker != null || pending != null || subscriptionListener != null) stopListening()
    }

    private fun status(text: String, force: Boolean) {
        prefs.edit().putString(Constants.PREF_SIGNAL_STATUS, text).apply()
        if (text != lastStatus || force) {
            lastStatus = text
            if (force || prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false)) LogStore.append(context, text)
        }
    }

    private fun detachCurrent() {
        generation++
        notificationEpoch++
        pending?.let { handler.removeCallbacks(it) }; pending = null
        registration?.let { try { it.detach() } catch (_: Exception) { } }; registration = null
        tracker = null; stable = null; latestObserved = null
    }

    private fun stopListening() {
        detachCurrent()
        if (Build.VERSION.SDK_INT >= 24) subscriptionListener?.let { try { subscriptions?.removeOnSubscriptionsChangedListener(it) } catch (_: Exception) { } }
        subscriptionListener = null; subscriptions = null; activeDataId = null
    }

    fun close() { closed = true; wifiMonitor.stop(); SignalCallMute.removeListener(quietListener); stopListening(); status("信号监听已停止", false); executor.shutdownNow() }

    @RequiresApi(31)
    private class ModernCallback(private val data: (Int) -> Unit, private val display: (Int, Int) -> Unit,
                                 private val activeData: (Int) -> Unit) : TelephonyCallback(), TelephonyCallback.DataConnectionStateListener,
        TelephonyCallback.DisplayInfoListener, TelephonyCallback.ActiveDataSubscriptionIdListener {
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) { data(networkType) }
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) { display(info.networkType, info.overrideNetworkType) }
        override fun onActiveDataSubscriptionIdChanged(subId: Int) { activeData(subId) }
    }

    private open class LegacyListener(private val data: (Int) -> Unit) : PhoneStateListener() {
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) { data(networkType) }
    }
    @RequiresApi(29)
    private open class DataSimListener(data: (Int) -> Unit, private val active: (Int) -> Unit) : LegacyListener(data) {
        override fun onActiveDataSubscriptionIdChanged(subId: Int) { active(subId) }
    }
    @RequiresApi(30)
    private class DisplayListener(data: (Int) -> Unit, private val display: (Int, Int) -> Unit, active: (Int) -> Unit) : DataSimListener(data, active) {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) { display(info.networkType, info.overrideNetworkType) }
    }
}
