package com.lanbing.smsforwarder.utils

import java.security.MessageDigest

data class AppNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val messageTime: Long = 0L,
    val notificationTime: Long = 0L,
    val groupSummary: Boolean = false,
    val ongoing: Boolean = false
)

data class NotificationForwardingOptions(
    val enabled: Boolean,
    val selectedPackages: Set<String>,
    val selfPackage: String,
    val phoneAndSmsPackages: Set<String>,
    val skipPhoneAndSms: Boolean = true,
    val skipOngoing: Boolean = true
)

/** Filtering is source-based: a chat app's CATEGORY_MESSAGE/CATEGORY_CALL is not an SMS/SIM call. */
fun acceptsNotification(notification: AppNotification, options: NotificationForwardingOptions): Boolean =
    options.enabled && notification.packageName != options.selfPackage &&
        notification.packageName in options.selectedPackages &&
        !(options.skipPhoneAndSms && notification.packageName in options.phoneAndSmsPackages) &&
        !notification.groupSummary && !(options.skipOngoing && notification.ongoing) &&
        (notification.title.isNotBlank() || notification.text.isNotBlank())

data class NotificationFingerprint(
    val key: String,
    val identityHash: String,
    val contentHash: String,
    val recordedAt: Long
)

/** Stores only bounded hashes, never notification text. Restore it after listener/process restarts. */
class NotificationDeduplicator(
    private val capacity: Int = 300,
    private val retentionMillis: Long = 24 * 60 * 60 * 1000L,
    restored: List<NotificationFingerprint> = emptyList()
) {
    private val entries = linkedMapOf<String, NotificationFingerprint>()

    init {
        require(capacity > 0 && retentionMillis > 0)
        restored.takeLast(capacity).forEach { entries[it.key] = it }
    }

    fun shouldForward(notification: AppNotification, nowMillis: Long): Boolean {
        entries.entries.removeAll { nowMillis - it.value.recordedAt >= retentionMillis || nowMillis < it.value.recordedAt }
        val content = hash(notification.packageName, notification.title, notification.text)
        val identity = hash(content, (notification.messageTime.takeIf { it > 0 } ?: notification.notificationTime).toString())
        val key = hash(notification.key)
        val previous = entries[key]
        if (previous != null) {
            if (previous.identityHash == identity) return false
            // Some apps recreate the same notification with a fresh "when" several times in a burst.
            // MessagingStyle has an explicit message timestamp, so genuinely new identical messages pass.
            if (notification.messageTime <= 0 && previous.contentHash == content &&
                nowMillis - previous.recordedAt < 3_000L) return false
        }
        entries.remove(key)
        entries[key] = NotificationFingerprint(key, identity, content, nowMillis)
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return true
    }

    fun forget(key: String): Boolean = entries.remove(hash(key)) != null
    fun snapshot(): List<NotificationFingerprint> = entries.values.toList()

    private fun hash(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update((bytes.size.toString() + ":").toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Keep the UTF-8 body below normal APNs payload limits without splitting surrogate pairs. */
fun formatAppNotification(notification: AppNotification, time: String, device: String): String {
    val message = buildString {
        append("【应用通知】${notification.appName.ifBlank { notification.packageName }}")
        if (notification.title.isNotBlank()) append("\n标题：${notification.title}")
        if (notification.text.isNotBlank()) append("\n内容：${notification.text}")
        append("\n通知时间：$time")
        if (device.isNotBlank()) append("\n设备：$device")
    }
    return truncateUtf8(message, 3_000)
}

internal fun truncateUtf8(text: String, maxBytes: Int): String {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
    val suffix = "\n…（内容已截断）"
    val budget = maxBytes - suffix.toByteArray(Charsets.UTF_8).size
    var end = 0
    var used = 0
    while (end < text.length) {
        val codePoint = text.codePointAt(end)
        val size = when {
            codePoint <= 0x7f -> 1
            codePoint <= 0x7ff -> 2
            codePoint <= 0xffff -> 3
            else -> 4
        }
        if (used + size > budget) break
        used += size
        end += Character.charCount(codePoint)
    }
    return text.substring(0, end) + suffix
}
