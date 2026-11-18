package com.lanbing.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore

/** Callbacks live with the forwarding service. System broadcasts share the same dispatcher. */
@Suppress("DEPRECATION")
class CallMonitor(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private data class Registration(val identity: Any, val label: String, val detach: () -> Unit)
    private val registrations = mutableMapOf<Int, Registration>()
    private var subscriptions: SubscriptionManager? = null
    private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var active = false
    private var generation = 0
    private var lastStatus: String? = null

    private fun allowed() = active && SignalCallMute.monitorNeeded(context)

    fun updateRegistration(diagnose: Boolean = false) {
        active = true
        if (!allowed()) {
            stopListening()
            CallDispatcher.resetIfDisabled(context)
            val reason = when {
                !prefs.getBoolean(Constants.PREF_ENABLED, false) -> "首页转发服务未开启"
                !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) -> "请先同意隐私政策"
                !prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false) -> "来电时推送未开启"
                else -> "缺少电话状态权限，无法监听来电"
            }
            status(reason, diagnose)
            return
        }
        if (diagnose) {
            // Explicit recheck must re-register, rather than report a possibly stale handle.
            generation++
            registrations.values.forEach { detach(it) }
            registrations.clear()
        }
        refreshSubscriptions(diagnose)
        if (Build.VERSION.SDK_INT >= 24 && subscriptionListener == null) {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() { if (allowed()) refreshSubscriptions() }
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) manager?.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
                else manager?.addOnSubscriptionsChangedListener(listener)
                subscriptions = manager
                subscriptionListener = listener
            } catch (e: Exception) {
                LogStore.append(context, "电话提醒：无法监听 SIM 变化 ${e.javaClass.simpleName}")
            }
        }
    }

    private fun refreshSubscriptions(diagnose: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val defaultManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (defaultManager == null) {
            status("系统电话服务不可用", diagnose)
            return
        }
        val targets = mutableMapOf<Int, Pair<TelephonyManager, String>>()
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                manager?.activeSubscriptionInfoList?.forEach { info ->
                    targets[info.subscriptionId] = defaultManager.createForSubscriptionId(info.subscriptionId) to "SIM ${info.simSlotIndex + 1}"
                }
            } catch (e: Exception) {
                LogStore.append(context, "电话提醒：读取 SIM 状态失败 ${e.javaClass.simpleName}")
            }
        }
        if (targets.isEmpty()) targets[-1] = defaultManager to "系统默认 SIM"
        CallDispatcher.setSubscriptions(targets.keys)
        SignalCallMute.retainSubscriptions(context, targets.keys)
        registrations.keys.toList().filter { it !in targets }.forEach { id ->
            registrations.remove(id)?.let { detach(it) }
        }
        targets.forEach { (id, target) -> if (id !in registrations) register(id, target.first, target.second) }
        if (registrations.isNotEmpty()) {
            val method = if (Build.VERSION.SDK_INT >= 31) "新电话接口" else "电话接口"
            val selected = prefs.getString(Constants.PREF_CALL_REMINDER_CHANNEL_ID, null)
            val channels = ChannelLoader.loadChannels(prefs).filter { selected == null || it.id == selected }
            val suffix = if (!prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false)) "；用于通话期间信号静默，电话推送未开启" else if (channels.isEmpty()) "；未选择有效推送通道" else "；通道 ${channels.joinToString { it.name }}"
            status("电话提醒监听已开启：$method，${registrations.values.joinToString { it.label }}$suffix", diagnose)
        } else status("电话接口监听失败；保留系统广播备用入口，请检查电话状态权限", diagnose)
    }

    private fun register(id: Int, manager: TelephonyManager, sim: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val identity = Any()
        val callbackGeneration = generation
        fun dispatch(state: Int, number: String?, source: String) {
            if (registrations[id]?.identity !== identity || callbackGeneration != generation || !allowed()) return
            val mapped = when (state) {
                TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
                TelephonyManager.CALL_STATE_IDLE -> CallState.IDLE
                else -> return
            }
            SignalCallMute.observe(context, id, mapped)
            CallDispatcher.onEvent(context, id.takeIf { it >= 0 }, sim, mapped, number, source)
        }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val callback = ModernCallback { dispatch(it, null, "新电话接口") }
                registrations[id] = Registration(identity, sim) { manager.unregisterTelephonyCallback(callback) }
                manager.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) { dispatch(state, phoneNumber, "电话接口") }
                }
                registrations[id] = Registration(identity, sim) { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
                manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            // Seed the mute gate before the signal monitor reads LTE during an existing call.
            // Do not manufacture a phone reminder from this snapshot.
            try {
                val state = if (Build.VERSION.SDK_INT >= 31) manager.callStateForSubscription else manager.callState
                val mapped = when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
                    TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
                    else -> CallState.IDLE
                }
                SignalCallMute.observe(context, id, mapped)
            } catch (_: Exception) { /* callbacks and protected broadcasts remain available */ }
        } catch (e: Exception) {
            registrations.remove(id)?.let { detach(it) }
            LogStore.append(context, "电话提醒监听失败 $sim：${e.javaClass.simpleName}，请检查电话状态权限")
        }
    }

    @RequiresApi(31)
    private class ModernCallback(private val changed: (Int) -> Unit) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) { changed(state) }
    }

    private fun status(text: String, force: Boolean) {
        prefs.edit().putString(Constants.PREF_CALL_LISTENER_STATUS, text).apply()
        if (text != lastStatus || force) {
            lastStatus = text
            if (prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false) || force) LogStore.append(context, text)
        }
    }

    private fun detach(registration: Registration) { try { registration.detach() } catch (_: Exception) { } }

    private fun stopListening() {
        active = false
        generation++
        registrations.values.forEach { detach(it) }
        registrations.clear()
        if (Build.VERSION.SDK_INT >= 24) {
            subscriptionListener?.let { listener -> try { subscriptions?.removeOnSubscriptionsChangedListener(listener) } catch (_: Exception) { } }
        }
        subscriptionListener = null
        subscriptions = null
    }

    fun close() {
        stopListening()
        CallDispatcher.resetIfDisabled(context)
        status("电话接口监听已停止", false)
    }
}
