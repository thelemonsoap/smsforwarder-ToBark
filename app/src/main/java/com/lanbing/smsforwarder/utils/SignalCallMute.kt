package com.lanbing.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore

/** Shared by broadcasts and service callbacks; only signal delivery consults this gate. */
object SignalCallMute {
    private val tracker = CallSignalQuietTracker()
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var initialized = false
    private var expiry: Runnable? = null
    @Volatile var epoch = 0L
        private set

    fun monitorNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_ENABLED, false) &&
            prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) &&
            (prefs.getBoolean(Constants.PREF_CALL_REMINDER_ENABLED, false) ||
                (prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false) &&
                    prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true))) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized private fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val remaining = if (prefs.getBoolean(Constants.PREF_SIGNAL_CALL_ACTIVE, false)) 30_000L else
            (prefs.getLong(Constants.PREF_SIGNAL_CALL_QUIET_UNTIL, 0L) - System.currentTimeMillis()).coerceIn(0L, 30_000L)
        tracker.restore(remaining, SystemClock.elapsedRealtime())
        schedule(context.applicationContext)
    }

    fun muted(context: Context): Boolean {
        initialize(context)
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)) return false
        return synchronized(this) { tracker.muted(SystemClock.elapsedRealtime()) }
    }

    fun addListener(listener: () -> Unit) { synchronized(this) { listeners.add(listener) } }
    fun removeListener(listener: () -> Unit) { synchronized(this) { listeners.remove(listener) } }

    fun observe(context: Context, id: Int, state: CallState, broadcast: Boolean = false) {
        initialize(context)
        change(context) { tracker.observe(id, state, broadcast, SystemClock.elapsedRealtime()) }
    }

    fun retainSubscriptions(context: Context, ids: Set<Int>) {
        initialize(context)
        change(context) { tracker.retainSubscriptions(ids, SystemClock.elapsedRealtime()) }
    }

    @Synchronized private fun change(context: Context, action: () -> Unit) {
        val before = tracker.busy to tracker.quietUntil
        action()
        if (before == (tracker.busy to tracker.quietUntil)) return
        epoch++
        persist(context)
        if (before.first != tracker.busy) log(context,
            if (tracker.busy) "信号提醒已暂停：电话响铃或通话中" else "通话结束：信号提醒将在30秒后恢复，期间不补发")
        schedule(context.applicationContext)
        listeners.toList().forEach { it() }
    }

    private fun log(context: Context, text: String) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.PREF_ENABLED, false) && prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) &&
            prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false) &&
            prefs.getBoolean(Constants.PREF_SIGNAL_PAUSE_DURING_CALLS, true)) LogStore.append(context, text)
    }

    @Synchronized private fun persist(context: Context) {
        val remaining = (tracker.quietUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Constants.PREF_SIGNAL_CALL_ACTIVE, tracker.busy)
            .putLong(Constants.PREF_SIGNAL_CALL_QUIET_UNTIL, if (remaining > 0) System.currentTimeMillis() + remaining else 0L).apply()
    }

    @Synchronized private fun schedule(context: Context) {
        expiry?.let { handler.removeCallbacks(it) }
        expiry = null
        if (tracker.busy || tracker.quietUntil <= SystemClock.elapsedRealtime()) return
        val task = Runnable {
            synchronized(this) {
                expiry = null
                epoch++
                persist(context)
                log(context, "通话静默结束：信号提醒已恢复，不补发期间的网络变化")
                listeners.toList().forEach { it() }
            }
        }
        expiry = task
        handler.postDelayed(task, tracker.quietUntil - SystemClock.elapsedRealtime())
    }
}
