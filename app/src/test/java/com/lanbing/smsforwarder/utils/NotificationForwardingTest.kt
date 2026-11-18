package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class NotificationForwardingTest {
    private val chat = "com.tencent.mm"
    private val sms = "com.google.android.apps.messaging"
    private val phone = "com.samsung.android.dialer"
    private val self = "com.lanbing.smsforwarder.bark"
    private fun notification() = AppNotification(
        key = "chat-1", packageName = chat, appName = "微信", title = "张三",
        text = "你好", postedAt = 1_000L, notificationTime = 1_000L
    )
    private fun options() = NotificationForwardingOptions(
        enabled = true, selectedPackages = setOf(chat, sms, phone, self), selfPackage = self,
        phoneAndSmsPackages = setOf(sms, phone, "carrier.custom.sms")
    )

    @Test fun selectedChatNotificationIsAccepted() {
        assertTrue(acceptsNotification(notification(), options()))
    }

    @Test fun disabledFeatureOrEmptySelectionDoesNotForward() {
        assertFalse(acceptsNotification(notification(), options().copy(enabled = false)))
        assertFalse(acceptsNotification(notification(), options().copy(selectedPackages = emptySet())))
    }

    @Test fun unselectedAppsAreNotForwarded() {
        assertFalse(acceptsNotification(notification().copy(packageName = "com.other.chat"), options()))
    }

    @Test fun smsAndPhoneNotificationsAreBlockedEvenIfSelectedToAvoidTwoSources() {
        assertFalse(acceptsNotification(notification().copy(packageName = sms), options()))
        assertFalse(acceptsNotification(notification().copy(packageName = phone), options()))
    }

    @Test fun dynamicallyDetectedDefaultSmsApplicationIsAlsoBlocked() {
        val selected = options().copy(selectedPackages = setOf("carrier.custom.sms"))
        assertFalse(acceptsNotification(notification().copy(packageName = "carrier.custom.sms"), selected))
    }

    @Test fun userCanExplicitlyOptIntoPhoneOrSmsNotificationForwarding() {
        assertTrue(acceptsNotification(notification().copy(packageName = sms), options().copy(skipPhoneAndSms = false)))
    }

    @Test fun ownNotificationsNeverForwardEvenIfAllOtherFiltersAreDisabled() {
        assertFalse(acceptsNotification(notification().copy(packageName = self), options().copy(skipPhoneAndSms = false, skipOngoing = false)))
    }

    @Test fun groupSummaryIsSkippedButItsIndividualMessageIsAccepted() {
        assertFalse(acceptsNotification(notification().copy(groupSummary = true), options()))
        assertTrue(acceptsNotification(notification(), options()))
    }

    @Test fun ongoingNotificationsAreSkippedByDefaultAndCanBeEnabled() {
        assertFalse(acceptsNotification(notification().copy(ongoing = true), options()))
        assertTrue(acceptsNotification(notification().copy(ongoing = true), options().copy(skipOngoing = false)))
    }

    @Test fun completelyEmptyNotificationsAreSkippedButTitleOnlyHasVisibleContent() {
        assertFalse(acceptsNotification(notification().copy(title = " ", text = ""), options()))
        val titleOnly = notification().copy(text = "")
        assertTrue(acceptsNotification(titleOnly, options()))
        assertTrue(formatAppNotification(titleOnly, "12:00", "").contains("标题：张三"))
    }

    @Test fun repostingTheSameNotificationDoesNotPushAgainWhenPostTimeChanges() {
        val dedup = NotificationDeduplicator()
        assertTrue(dedup.shouldForward(notification(), 1_000L))
        assertFalse(dedup.shouldForward(notification().copy(postedAt = 100_000L), 100_000L))
    }

    @Test fun newMessageContentUsingTheSameNotificationKeyIsForwarded() {
        val dedup = NotificationDeduplicator()
        dedup.shouldForward(notification(), 1_000L)
        assertTrue(dedup.shouldForward(notification().copy(text = "第二条新消息"), 1_100L))
    }

    @Test fun identicalMessagesWithDifferentMessagingTimestampsAreNotLost() {
        val dedup = NotificationDeduplicator()
        assertTrue(dedup.shouldForward(notification().copy(messageTime = 1_000L), 1_000L))
        assertTrue(dedup.shouldForward(notification().copy(messageTime = 1_100L), 1_100L))
    }

    @Test fun burstRecreationOfGenericNotificationWithSameContentIsSuppressed() {
        val dedup = NotificationDeduplicator()
        dedup.shouldForward(notification(), 1_000L)
        assertFalse(dedup.shouldForward(notification().copy(notificationTime = 1_100L), 1_100L))
        assertTrue(dedup.shouldForward(notification().copy(notificationTime = 5_000L), 5_000L))
    }

    @Test fun twoNotificationsWithSameTextAndDifferentKeysStayIndependent() {
        val dedup = NotificationDeduplicator()
        assertTrue(dedup.shouldForward(notification(), 1_000L))
        assertTrue(dedup.shouldForward(notification().copy(key = "chat-2"), 1_001L))
    }

    @Test fun removingNotificationAllowsNewMessageToReuseItsKey() {
        val dedup = NotificationDeduplicator()
        dedup.shouldForward(notification(), 1_000L)
        dedup.forget(notification().key)
        assertTrue(dedup.shouldForward(notification(), 1_100L))
    }

    @Test fun listenerRestartRestoresDedupHashesWithoutSavingTextOrOriginalKey() {
        val dedup = NotificationDeduplicator()
        dedup.shouldForward(notification(), 1_000L)
        val stored = dedup.snapshot()
        assertFalse(stored.toString().contains("你好"))
        assertFalse(stored.toString().contains("张三"))
        assertFalse(stored.toString().contains("chat-1"))
        assertFalse(NotificationDeduplicator(restored = stored).shouldForward(notification(), 5_000L))
    }

    @Test fun boundedCacheExpiresAndDoesNotSuppressMessagesForever() {
        val dedup = NotificationDeduplicator(capacity = 2, retentionMillis = 10_000L)
        dedup.shouldForward(notification(), 1_000L)
        dedup.shouldForward(notification().copy(key = "chat-2"), 2_000L)
        dedup.shouldForward(notification().copy(key = "chat-3"), 3_000L)
        assertEquals(2, dedup.snapshot().size)
        assertTrue(dedup.shouldForward(notification(), 4_000L))
        assertTrue(dedup.shouldForward(notification(), 15_000L))
    }

    @Test fun plainTextPreservesUnicodeQuotesAndNewlines() {
        val message = formatAppNotification(notification().copy(text = "引号：\"中文\"\nemoji：🔑"), "2026-10-03 11:00", "Samsung")
        assertTrue(message.contains("【应用通知】微信"))
        assertTrue(message.contains("引号：\"中文\"\nemoji：🔑"))
        assertTrue(message.contains("通知时间：2026-10-03 11:00"))
    }

    @Test fun longBodyIsLimitedByUtf8BytesWithoutBreakingEmoji() {
        val message = formatAppNotification(notification().copy(text = "🔑中文".repeat(2_000)), "12:00", "Samsung")
        assertTrue(message.toByteArray(Charsets.UTF_8).size <= 3_000)
        assertTrue(message.endsWith("…（内容已截断）"))
        assertFalse(message.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8).contains("\uFFFD"))
        assertFalse(message.contains('?'))
    }
}
