package com.lanbing.smsforwarder.widget

import android.content.Context

/** Local snapshot of the last HTTP-successful outgoing message; no history scan or polling. */
object SentMessageStore {
    private const val PREFS = "widget_last_sent"
    private val lock = Any()
    data class Snapshot(val text: String = "", val sentAt: Long = 0L)

    fun read(context: Context): Snapshot = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Snapshot(prefs.getString("text", "").orEmpty(), prefs.getLong("sent_at", 0L))
    }

    fun record(context: Context, message: String, sentAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString("text", message.take(1200)).putLong("sent_at", sentAt).apply()
        }
        ServiceStatusWidget.updateAll(context)
    }
}
