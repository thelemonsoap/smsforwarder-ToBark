package com.lanbing.smsforwarder

import android.app.Application
import com.lanbing.smsforwarder.utils.WebhookSender
import com.lanbing.smsforwarder.widget.SentMessageStore

class ForwarderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Wiring only: no widget refresh, service start, timer, or network operation at startup.
        WebhookSender.onMessageSent = { message -> SentMessageStore.record(this, message) }
    }
}
