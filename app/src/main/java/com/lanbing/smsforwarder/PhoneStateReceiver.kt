package com.lanbing.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.lanbing.smsforwarder.utils.CallDispatcher
import com.lanbing.smsforwarder.utils.CallState
import com.lanbing.smsforwarder.utils.SignalCallMute
import java.util.concurrent.atomic.AtomicBoolean

/** System-only PHONE_STATE is a fallback; it never starts a background foreground service. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED || !SignalCallMute.monitorNeeded(context)) return
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> CallState.RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallState.OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> CallState.IDLE
            else -> return
        }
        var subId = if (Build.VERSION.SDK_INT >= 26) intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1) else -1
        // AOSP and some OEM PHONE_STATE implementations still populate the legacy extra.
        if (subId < 0) subId = intent.getIntExtra("subscription", -1)
        val slot = if (Build.VERSION.SDK_INT >= 30) intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1) else -1
        SignalCallMute.observe(context, subId, state, broadcast = true)
        if (!CallDispatcher.allowed(context)) return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val pending = goAsync()
        val completed = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val finish = Runnable { if (completed.compareAndSet(false, true)) pending.finish() }
        // Finish every broadcast within its time budget even if the network is unavailable.
        handler.postDelayed(finish, 8_000L)
        CallDispatcher.onEvent(context, subId.takeIf { it >= 0 }, if (slot >= 0) "SIM ${slot + 1}" else "",
            state, number, "系统广播") {
            handler.removeCallbacks(finish)
            finish.run()
        }
    }
}
